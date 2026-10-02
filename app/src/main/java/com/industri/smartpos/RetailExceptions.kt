package com.industri.smartpos

import java.io.IOException

class ProductBarcodeNotFoundException(
    val barcode: String
) : Exception(
    "Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem."
)

class PaymentGatewayTimeoutException(
    val gatewayName: String
) : IOException(
    "Layanan pembayaran $gatewayName tidak merespons dalam 15 detik."
)

class CashierLimitExceededException(
    val maxLimit: Double
) : Exception(
    "Total transaksi melebihi limit otorisasi kasir mandiri."
)
