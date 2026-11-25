package id.sch.santaagnes.sim

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.core.SessionEvents
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepository
import id.sch.santaagnes.sim.data.repository.NotificationRepository
import id.sch.santaagnes.sim.databinding.ActivityMainBinding
import id.sch.santaagnes.sim.notification.PushTokenRegistrar
import id.sch.santaagnes.sim.ui.auth.navigateToLogin
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var sessionEvents: SessionEvents

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var calendarSyncRepository: CalendarSyncRepository

    // Izin tampil notifikasi (Android 13+); jika ditolak, riwayat tetap tersimpan di tab Notifikasi.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Menu bottom nav tiap role; id item sama dengan id tujuan tab di graph role itu.
    private val tabMenus = mapOf(
        R.id.student_graph to R.menu.menu_bottom_student,
        R.id.guardian_graph to R.menu.menu_bottom_guardian,
        R.id.admin_graph to R.menu.menu_bottom_admin,
        R.id.teacher_graph to R.menu.menu_bottom_teacher
    )
    private val notificationTabIds = listOf(
        R.id.studentNotificationsFragment,
        R.id.guardianNotificationsFragment,
        R.id.teacherNotificationsFragment,
        R.id.adminNotificationsFragment
    )
    private var inflatedMenu: Int? = null
    private var notificationPermissionAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ContextCompat.getColor(this, R.color.agnesian_blue)),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            binding.statusBarScrim.layoutParams = binding.statusBarScrim.layoutParams.apply {
                height = systemBars.top
            }
            view.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // Inset bawah sudah dipasang pada root di atas; BottomNavigationView tidak boleh menambahnya lagi
        // (kalau tidak, muncul ruang kosong besar di bawah label tab).
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController

        setupBottomNav(navController)
        setupPush()

        // Refresh token ditolak server: sesi sudah dihapus di lapisan jaringan, kembalikan ke login.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionEvents.expired.collect {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.session_expired,
                        Toast.LENGTH_LONG
                    ).show()
                    navController.navigateToLogin()
                }
            }
        }
    }

    // Dengan sesi tersimpan (auto-login) token FCM didaftarkan ulang; untuk login baru dilakukan
    // AuthRepository. Gagal diabaikan, dicoba lagi saat aplikasi dibuka berikutnya.
    private fun setupPush() {
        lifecycleScope.launch {
            if (!sessionManager.hasSession()) return@launch
            pushTokenRegistrar.register()
            notificationRepository.syncAnnouncements()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationRepository.observeUnreadCount().collect(::showUnreadBadge)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            if (sessionManager.hasSession()) {
                // Coba daftarkan ulang token FCM bila sebelumnya gagal saat login.
                pushTokenRegistrar.register()
                notificationRepository.syncAnnouncements()
                syncDeviceCalendar()
            }
        }
    }

    // Sinkron kalender perangkat (F-JDW-06) hanya jalan bila pengguna menyalakannya di Profil; gagal diabaikan.
    private suspend fun syncDeviceCalendar() {
        val userId = sessionManager.getUserId() ?: return
        val role = sessionManager.getRole() ?: return
        calendarSyncRepository.syncIfEnabled(userId, role)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Tab Notifikasi tiap role punya id sendiri; badge dipasang pada yang ada di menu saat ini.
    private fun showUnreadBadge(count: Int) {
        notificationTabIds.forEach { id ->
            if (binding.bottomNav.menu.findItem(id) == null) return@forEach
            if (count > 0) {
                binding.bottomNav.getOrCreateBadge(id).number = count
            } else {
                binding.bottomNav.removeBadge(id)
            }
        }
    }

    private fun setupBottomNav(navController: NavController) {
        binding.bottomNav.setOnItemSelectedListener { item ->
            // Tiap tab menyimpan back stack-nya sendiri; kembali ke tab awal graph role.
            val graph = navController.currentDestination?.parent
            // Tab Home = tujuan awal graph. Bila layar lain didorong di atas Home (mis. "Lihat Semua Kelas"),
            // kombinasi popUpTo + singleTop tidak mengganti layar, jadi cukup kembali ke Home.
            if (item.itemId == graph?.startDestinationId && navController.popBackStack(item.itemId, false)) {
                return@setOnItemSelectedListener true
            }
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(graph?.startDestinationId ?: item.itemId, false, true)
                .build()
            navController.navigate(item.itemId, null, options)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menu = tabMenus[destination.parent?.id]
            if (menu == null) {
                binding.bottomNav.isVisible = false
                return@addOnDestinationChangedListener
            }
            showMenu(menu)
            // Izin diminta sekali setelah pengguna masuk ke aplikasi (login baru maupun auto-login).
            if (!notificationPermissionAsked) {
                notificationPermissionAsked = true
                requestNotificationPermission()
            }
            // Sub-tab Master Data (Siswa/Guru/Kelas) tetap terhitung tab Akademik agar BottomNav & highlight terjaga.
            val mappedTabId = when (destination.id) {
                R.id.adminStudentFragment,
                R.id.adminTeacherFragment,
                R.id.adminClassFragment -> R.id.adminUserFragment
                else -> destination.id
            }
            // Layar turunan (mis. form tugas) bukan tab: bottom nav disembunyikan.
            val tab = binding.bottomNav.menu.findItem(mappedTabId)
            binding.bottomNav.isVisible = tab != null
            tab?.isChecked = true
        }
    }

    private fun showMenu(@MenuRes menuRes: Int) {
        if (inflatedMenu == menuRes) return
        binding.bottomNav.menu.clear()
        binding.bottomNav.inflateMenu(menuRes)
        inflatedMenu = menuRes
    }
}
