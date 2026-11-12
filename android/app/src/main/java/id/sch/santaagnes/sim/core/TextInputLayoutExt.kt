package id.sch.santaagnes.sim.core

import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

/**
 * Menghapus pesan error kolom begitu isinya berubah (diketik, dipilih dari dropdown, atau
 * diisi dari date/time picker). Error dipasang ulang oleh ViewModel saat Simpan ditekan lagi.
 */
fun TextInputLayout.clearErrorOnEdit() {
    editText?.doAfterTextChanged { error = null }
}
