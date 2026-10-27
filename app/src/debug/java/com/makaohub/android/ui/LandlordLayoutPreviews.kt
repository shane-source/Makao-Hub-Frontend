package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.makaohub.android.OwnerScreen
import com.makaohub.android.data.LandlordSummary
import com.makaohub.android.data.OwnerApplication
import com.makaohub.android.data.OwnerListing

/** Synthetic display data only; these previews never create a model, session or network request. */
@Preview(name = "Landlord home · design width", widthDp = 402, heightDp = 1100, showSystemUi = true)
@Preview(name = "Landlord home · compact / larger text", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LandlordDashboardPreview() {
    val draft = OwnerListing("preview-listing", "Garden Studio", "Preview only", "STUDIO",
        18000L, 1, 1, false, null, "DRAFT", "2026-10-21T06:00:00Z", "2026-10-21T06:00:00Z")
    MakaoTheme {
        Scaffold(containerColor = Cream, bottomBar = {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                OwnerBottomBar(OwnerScreen.HOME, true) {}
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                OwnerHeader(true) {}; Gap(20)
                OwnerDashboard("Preview Owner", "VERIFIED", LandlordSummary(4, 1, 2),
                    listOf(draft, draft.copy(id = "preview-paused", status = "PAUSED")),
                    listOf(OwnerApplication("preview-application", draft.id, "SUBMITTED", draft.createdAt, draft.updatedAt)),
                    true, false, {}, {}, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Landlord white form cards", widthDp = 402, heightDp = 650, showSystemUi = true)
@Composable
private fun LandlordFieldsPreview() {
    MakaoTheme {
        Scaffold(containerColor = Cream) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                OwnerHeader(true) {}; Gap(20)
                OwnerHeading("Tell us about the property", "Step 1 of 4 · Whole property or unit")
                OwnerInput("Listing title", "Preview property")
                OwnerInput("Bedrooms", "2", numeric = true)
                OwnerInput("Description", "A bright apartment with a balcony.", lines = 2)
                PrimaryButton("Save and continue", true) {}
            }
        }
    }
}
