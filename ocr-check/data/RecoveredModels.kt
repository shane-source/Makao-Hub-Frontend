package com.makaohub.android.data

data class InboxNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: String,
    val bookingId: String? = null
)

class CompletePhoneRequest(val phoneNumber: String)
class AccountActionStartRequest(val email: String, val action: String)
class AccountActionConfirmRequest(
    val email: String,
    val action: String,
    val challengeId: String,
    val code: String
)
class AvatarUploadRequest(val contentType: String)
class AvatarConfirmRequest(val objectPath: String)
class AvatarCapabilities(val validation: String)
class AvatarUploadTicket(val uploadUrl: String, val objectPath: String, val contentType: String)

data class OwnerTestDestinationStatus(val configured: Boolean, val eligible: Boolean = false)
class OwnerTestDestinationRequest(val phone: String, val ownNumberConfirmed: Boolean = true)

data class LandlordOfferQuote(
    val code: String,
    val amountMinor: Long,
    val days: Long,
    val listingLimit: Int,
    val estimatedReach: Int = 0,
    val photoLimit: Int = 0,
    val includedBoosts: Int = 0
)
data class LandlordOfferGrant(val code: String, val listingId: String?, val startsAt: String, val expiresAt: String)
data class LandlordOffers(
    val starterLimit: Int,
    val activeListingLimit: Int,
    val offers: List<LandlordOfferQuote>,
    val activeGrants: List<LandlordOfferGrant> = emptyList(),
    val freePhotoLimit: Int = 2,
    val activePhotoLimit: Int = 2,
    val boostCredits: Int = 0
)
data class LandlordInsight(val listingId: String, val title: String, val detailOpens: Long, val enquiries: Long)
data class ListingCardMetrics(val listingId: String, val viewsToday: Long, val boostExpiresAt: String?)
class LandlordOfferPurchase(val code: String, val listingId: String?)

class BoostRedeemBody(val listingId: String)
data class BoostRedeemResult(val boostExpiresAt: String? = null)

data class OwnerBooking(
    val id: String, val sourceApplicationId: String, val status: String, val totalAmountMinor: Long = 0,
    val renterAgreedAt: String? = null, val ownerAgreedAt: String? = null,
    val ownerMoveInConfirmedAt: String? = null, val moveInAccepted: Boolean? = null,
    val moveInAnsweredAt: String? = null, val releasedAt: String? = null,
    val testPayoutStatus: String? = null, val renterName: String? = null
)
class MoveInAnswer(val accepted: Boolean)
