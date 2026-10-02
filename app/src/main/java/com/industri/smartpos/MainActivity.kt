package com.industri.smartpos

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.industri.smartpos.databinding.ActivityMainBinding
import timber.log.Timber

import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnHitung.setOnClickListener {
            hitungTotalHarga()
        }

        binding.btnBukaPos.setOnClickListener {
            val intent = Intent(this, PosActivity::class.java)
            startActivity(intent)
        }
    }

    private fun hitungTotalHarga() {
        val nominalStr = binding.etNominal.text.toString()
        val qtyStr = binding.etQty.text.toString()

        Timber.d("Input nominalStr: %s, qtyStr: %s", nominalStr, qtyStr)

        val kalkulasiResult = runCatching {
            val nominal = nominalStr.toDoubleOrNull()
                ?: throw IllegalArgumentException("Nominal tidak boleh kosong dan harus berupa angka valid")
            val qty = qtyStr.toIntOrNull()
                ?: throw IllegalArgumentException("Kuantitas tidak boleh kosong dan harus berupa angka bulat")

            if (qty <= 0) {
                throw IllegalArgumentException("Kuantitas barang minimal 1")
            }

            nominal * qty
        }

        kalkulasiResult.onSuccess { total ->
            Timber.i("Kalkulasi sukses: total = %.2f", total)
            binding.tvHasil.text = "Total Harga: Rp${String.format(Locale.getDefault(), "%,.0f", total)}"
        }.onFailure { err ->
            Timber.e(err, "Kalkulasi gagal: %s", err.message)
            binding.tvHasil.text = "Error: ${err.message ?: "Input tidak valid"}"
        }
    }
}
