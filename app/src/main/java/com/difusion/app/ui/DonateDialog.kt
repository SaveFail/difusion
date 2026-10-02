package com.difusion.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.difusion.app.R

/** Enlace universal de Binance Pay (abre la app si está instalada o la web). */
const val DONATE_URL = "https://app.binance.com/uni-qr/keieoYWm"

/** Abre la billetera de Binance; si no hay manejador, cae a la tienda o a la web. */
fun openBinance(context: Context) {
    val direct = Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
        return
    } catch (_: ActivityNotFoundException) {
    }
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.binance.dev"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
        return
    } catch (_: ActivityNotFoundException) {
    }
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.binance.com/en/download"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Binance", text))
}

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFF0B90B)
            )
        },
        title = {
            Text(
                "Apoyar el proyecto",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Si esta app te resulta útil, puedes hacer una donación con Binance Pay. ¡Gracias por el apoyo!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.size(216.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.donar_binance_qr),
                        contentDescription = "Código QR para donar con Binance",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
                Text(
                    "Escanea el QR con la app de Binance o toca \"Abrir Binance\" para ir directo a la billetera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            PrimaryActionButton(
                text = "Abrir Binance",
                onClick = { openBinance(context) },
                icon = Icons.Default.AccountBalanceWallet
            )
        },
        dismissButton = {
            SecondaryActionButton(
                text = "Copiar enlace",
                onClick = {
                    copyToClipboard(context, DONATE_URL)
                    Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Default.ContentCopy
            )
        }
    )
}
