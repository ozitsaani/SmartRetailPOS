package com.industri.smartpos

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.industri.smartpos.databinding.ActivityPosBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPosBinding
    private var retryAttempt = 0
    private var isRetryAction = false

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        Timber.e(exception, "FATAL COROUTINE ERROR TERTANGKAP: %s", exception.message)

        runOnUiThread {
            setLoadingState(false)
            updateStatusCard(
                header = "Error Terjadi",
                sub = exception.message ?: "Terjadi kesalahan sistem coroutine",
                badge = "!",
                bgColor = Color.parseColor("#7F1D1D"),
                strokeColor = Color.parseColor("#DC2626"),
                headerTextColor = Color.parseColor("#FCA5A5"),
                subTextColor = Color.parseColor("#FECACA"),
                retryText = "Sistem dipulihkan secara aman"
            )
            tampilkanSnackbarError(
                pesan = "Sistem mengalami error: ${exception.message}",
                canRetry = false
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnProcessPayment.setOnClickListener {
            isRetryAction = false
            prosesTransaksi()
        }
    }

    private fun prosesTransaksi() {
        val barcode = binding.etBarcode.text?.toString()?.trim().orEmpty()
        val amountStr = binding.etPosAmount.text?.toString()?.trim().orEmpty()

        if (barcode.isEmpty()) {
            tampilkanSnackbarError("Barcode produk wajib diisi / dipindai!", canRetry = false)
            binding.etBarcode.error = "Barcode wajib diisi"
            return
        }

        val totalAmount = amountStr.toDoubleOrNull() ?: 21500.0

        if (!isRetryAction) {
            retryAttempt = 0
        }

        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.Main + coroutineExceptionHandler) {
            Timber.i("Memulai simulasi transaksi untuk barcode: %s, nominal: %.2f", barcode, totalAmount)

            // Simulasi delay latency jaringan
            withContext(Dispatchers.IO) {
                delay(1200)
            }

            val hasil = runCatching {
                // Check Cashier Limit Exceeded (Tugas Mandiri 2)
                if (totalAmount > 10_000_000.0) {
                    throw CashierLimitExceededException(10_000_000.0)
                }

                when {
                    binding.rbBarcodeError.isChecked -> {
                        throw ProductBarcodeNotFoundException(barcode)
                    }

                    binding.rbNetworkTimeout.isChecked -> {
                        throw PaymentGatewayTimeoutException("QRIS Bank Settlement")
                    }

                    else -> {
                        PosTransaction(
                            transactionId = "TRX-2026-${(1000..9999).random()}",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = totalAmount,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }

            setLoadingState(false)

            hasil.onSuccess { transaction ->
                retryAttempt = 0
                isRetryAction = false
                Timber.i("Transaksi sukses: ID=%s", transaction.transactionId)

                tampilkanHasilTransaksiSukses(transaction)
            }.onFailure { err ->
                Timber.w(err, "Transaksi gagal: %s", err.message)
                tanganiErrorTransaksi(err, barcode, totalAmount)
            }
        }
    }

    private fun tampilkanHasilTransaksiSukses(trx: PosTransaction) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedAmount = String.format(Locale.getDefault(), "%,.0f", trx.totalAmount)

        binding.cardPosResult.visibility = View.VISIBLE
        binding.tvPosStatus.text = "✓ TRANSAKSI BERHASIL (LUNAS)"
        binding.tvPosStatus.setTextColor(Color.parseColor("#34D399"))

        val detailText = """
            ID Transaksi : ${trx.transactionId}
            Barcode      : ${trx.barcode}
            Produk       : ${trx.productName}
            Total Bayar  : Rp$formattedAmount
            Status       : ${trx.status}
            Waktu        : $timeStamp
        """.trimIndent()

        binding.tvPosDetails.text = detailText

        updateStatusCard(
            header = "Sistem Normal",
            sub = "✓ Transaksi Berhasil (LUNAS)",
            badge = "✓",
            bgColor = Color.parseColor("#064E3B"),
            strokeColor = Color.parseColor("#059669"),
            headerTextColor = Color.parseColor("#34D399"),
            subTextColor = Color.parseColor("#A7F3D0"),
            retryText = "Koneksi pembayaran normal"
        )
    }

    private fun tanganiErrorTransaksi(err: Throwable, barcode: String, totalAmount: Double) {
        binding.cardPosResult.visibility = View.GONE

        when (err) {
            is ProductBarcodeNotFoundException -> {
                updateStatusCard(
                    header = "Barcode Tidak Ditemukan",
                    sub = "✕ Barang dengan barcode [$barcode] tidak terdaftar.",
                    badge = "✕",
                    bgColor = Color.parseColor("#7F1D1D"),
                    strokeColor = Color.parseColor("#DC2626"),
                    headerTextColor = Color.parseColor("#FCA5A5"),
                    subTextColor = Color.parseColor("#FECACA"),
                    retryText = "Periksa kembali barcode produk"
                )
                tampilkanSnackbarError(
                    pesan = err.message ?: "Barcode produk tidak ditemukan dalam katalog.",
                    canRetry = false
                )
            }

            is PaymentGatewayTimeoutException -> {
                retryAttempt++
                updateStatusCard(
                    header = "Payment Gateway Timeout",
                    sub = "⚠ Layanan QRIS Bank Settlement tidak merespons.",
                    badge = "⚠",
                    bgColor = Color.parseColor("#78350F"),
                    strokeColor = Color.parseColor("#D97706"),
                    headerTextColor = Color.parseColor("#FDE68A"),
                    subTextColor = Color.parseColor("#FEF3C7"),
                    retryText = "Percobaan jaringan: $retryAttempt / 3"
                )

                if (retryAttempt >= 3) {
                    tampilkanDialogFallbackTunai()
                } else {
                    tampilkanSnackbarError(
                        pesan = "Koneksi pembayaran timeout (Percobaan $retryAttempt/3)",
                        canRetry = true
                    )
                }
            }

            is CashierLimitExceededException -> {
                updateStatusCard(
                    header = "Limit Otorisasi Terlampaui",
                    sub = "🔐 Total transaksi melebihi limit otorisasi kasir mandiri.",
                    badge = "🔐",
                    bgColor = Color.parseColor("#581C87"),
                    strokeColor = Color.parseColor("#9333EA"),
                    headerTextColor = Color.parseColor("#E9D5FF"),
                    subTextColor = Color.parseColor("#F3E8FF"),
                    retryText = "Membutuhkan Otorisasi Supervisor"
                )
                tampilkanDialogSupervisor(totalAmount, barcode)
            }

            else -> {
                updateStatusCard(
                    header = "Error Transaksi",
                    sub = err.message ?: "Terjadi kesalahan operasional.",
                    badge = "!",
                    bgColor = Color.parseColor("#7F1D1D"),
                    strokeColor = Color.parseColor("#DC2626"),
                    headerTextColor = Color.parseColor("#FCA5A5"),
                    subTextColor = Color.parseColor("#FECACA"),
                    retryText = "Operasi dibatalkan"
                )
                tampilkanSnackbarError(
                    pesan = err.message ?: "Terjadi kesalahan yang tidak terduga.",
                    canRetry = false
                )
            }
        }
    }

    private fun tampilkanSnackbarError(pesan: String, canRetry: Boolean) {
        val snackbar = Snackbar.make(binding.coordinatorLayout, pesan, Snackbar.LENGTH_LONG)

        if (canRetry) {
            snackbar.setAction("COBA LAGI") {
                if (retryAttempt < 3) {
                    isRetryAction = true
                    prosesTransaksi()
                } else {
                    tampilkanDialogFallbackTunai()
                }
            }
            snackbar.setActionTextColor(Color.parseColor("#FBBF24"))
        }

        snackbar.show()
    }

    private fun tampilkanDialogFallbackTunai() {
        AlertDialog.Builder(this)
            .setTitle("Pembayaran Tidak Dapat Diproses")
            .setMessage("Koneksi payment gateway masih bermasalah setelah 3 kali percobaan. Silakan alihkan pelanggan ke Pembayaran Tunai Manual.")
            .setCancelable(false)
            .setPositiveButton("Pembayaran Tunai Manual") { dialog, _ ->
                dialog.dismiss()
                prosesPembayaranTunaiManual()
            }
            .setNegativeButton("Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun prosesPembayaranTunaiManual() {
        retryAttempt = 0
        isRetryAction = false

        val barcode = binding.etBarcode.text?.toString()?.trim().orEmpty()
        val amountStr = binding.etPosAmount.text?.toString()?.trim().orEmpty()
        val totalAmount = amountStr.toDoubleOrNull() ?: 21500.0

        val trx = PosTransaction(
            transactionId = "TRX-CASH-${(1000..9999).random()}",
            barcode = barcode,
            productName = "Susu UHT Full Cream 1 Liter (Tunai)",
            totalAmount = totalAmount,
            status = "SETTLED_MANUAL_CASH"
        )

        tampilkanHasilTransaksiSukses(trx)
        Toast.makeText(this, "Diutihkan ke Pembayaran Tunai Manual Berhasil", Toast.LENGTH_LONG).show()
    }

    private fun tampilkanDialogSupervisor(totalAmount: Double, barcode: String) {
        val formattedAmount = String.format(Locale.getDefault(), "%,.0f", totalAmount)

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        val padding = (20 * resources.displayMetrics.density).toInt()
        params.setMargins(padding, padding / 2, padding, padding / 2)

        val inputPin = EditText(this).apply {
            hint = "Masukkan PIN Supervisor"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            layoutParams = params
            setTextColor(Color.parseColor("#F3F4F6"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
        }
        container.addView(inputPin)

        AlertDialog.Builder(this)
            .setTitle("Otorisasi Supervisor")
            .setMessage("Total transaksi (Rp$formattedAmount) melebihi limit kasir mandiri (Rp10.000.000).\nDiperlukan otorisasi Supervisor untuk melanjutkan transaksi.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Otorisasi") { dialog, _ ->
                val pin = inputPin.text.toString().trim()
                dialog.dismiss()

                // KEAMANAN: Jangan pernah mencatat PIN ke Logcat / Timber
                if (pin == "1234" || pin == "8888" || pin.length >= 4) {
                    Timber.i("Otorisasi Supervisor BERHASIL untuk transaksi di atas limit.")
                    Toast.makeText(this, "Otorisasi Supervisor Berhasil", Toast.LENGTH_SHORT).show()

                    val trx = PosTransaction(
                        transactionId = "TRX-SUP-${(1000..9999).random()}",
                        barcode = barcode,
                        productName = "Susu UHT Full Cream 1 Liter (Supervisor Approved)",
                        totalAmount = totalAmount,
                        status = "SETTLED_SUPERVISOR_APPROVED"
                    )
                    tampilkanHasilTransaksiSukses(trx)
                } else {
                    Timber.w("Otorisasi Supervisor GAGAL: PIN tidak valid.")
                    Toast.makeText(this, "PIN Supervisor Salah! Transaksi Dibatalkan.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
                tampilkanSnackbarError("Transaksi dibatalkan. Diperlukan otorisasi Supervisor.", canRetry = false)
            }
            .show()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.pbPosLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnProcessPayment.isEnabled = !isLoading
        binding.etBarcode.isEnabled = !isLoading
        binding.etPosAmount.isEnabled = !isLoading
        binding.rgSimulation.isEnabled = !isLoading
        for (i in 0 until binding.rgSimulation.childCount) {
            binding.rgSimulation.getChildAt(i).isEnabled = !isLoading
        }
    }

    private fun updateStatusCard(
        header: String,
        sub: String,
        badge: String,
        bgColor: Int,
        strokeColor: Int,
        headerTextColor: Int,
        subTextColor: Int,
        retryText: String
    ) {
        binding.cardSystemStatus.setCardBackgroundColor(bgColor)
        binding.cardSystemStatus.strokeColor = strokeColor
        binding.tvStatusBadge.text = badge
        binding.tvStatusBadge.setTextColor(headerTextColor)
        binding.tvStatusHeader.text = header
        binding.tvStatusHeader.setTextColor(headerTextColor)
        binding.tvStatusSub.text = sub
        binding.tvStatusSub.setTextColor(subTextColor)
        binding.tvRetryProgressIndicator.text = retryText
        binding.tvRetryProgressIndicator.setTextColor(subTextColor)
    }
}
