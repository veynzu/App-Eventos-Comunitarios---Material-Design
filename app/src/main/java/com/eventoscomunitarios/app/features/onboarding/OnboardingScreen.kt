package com.eventoscomunitarios.app.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme

@Composable
fun OnboardingScreen(
    onContinuar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.comparte_y_conecta
            ),
            contentDescription = "Logo de Share You",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Descubre, comparte y conecta con tu comunidad",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinuar
        ) {
            Text(text = "Comenzar")
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Onboarding"
)
@Composable
private fun OnboardingScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        OnboardingScreen(onContinuar = {})
    }
}
