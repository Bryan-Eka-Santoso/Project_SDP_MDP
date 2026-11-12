package id.sch.santaagnes.sim

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

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
            requestNotificationPermission()
            pushTokenRegistrar.register()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationRepository.observeUnreadCount().collect(::showUnreadBadge)
            }
        }
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
            // Layar turunan (mis. form tugas) bukan tab: bottom nav disembunyikan.
            val tab = binding.bottomNav.menu.findItem(destination.id)
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
