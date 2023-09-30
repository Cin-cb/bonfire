package com.dzen.campfire.screens.intro

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dzen.campfire.R
import com.dzen.campfire.api.API
import com.dzen.campfire.api.API_RESOURCES
import com.dzen.campfire.api.API_TRANSLATE
import com.dzen.campfire.api.models.account.Account
import com.dzen.campfire.api.requests.accounts.RAccountsAddNotificationsToken
import com.dzen.campfire.api.requests.accounts.RAccountsGetInfo
import com.dzen.campfire.api.requests.accounts.RAccountsLogin
import com.dzen.campfire.api.requests.accounts.RAccountsRegistration
import com.dzen.campfire.api.requests.project.RProjectGetLoadingPictures
import com.dzen.campfire.app.App
import com.dzen.campfire.screens.hello.SCampfireHello
import com.google.firebase.auth.FirebaseAuth
import com.sayzen.campfiresdk.controllers.*
import com.sayzen.campfiresdk.models.objects.MChatMessagesPool
import com.sayzen.devsupandroidgoogle.ControllerGoogleAuth
import com.sup.dev.android.libs.image_loader.ImageLoader
import com.sup.dev.android.libs.screens.Screen
import com.sup.dev.android.libs.screens.navigator.Navigator
import com.sup.dev.android.tools.ToolsBitmap
import com.sup.dev.android.tools.ToolsResources
import com.sup.dev.android.tools.ToolsStorage
import com.sup.dev.android.tools.ToolsToast
import com.sup.dev.java.libs.debug.err
import com.sup.dev.java.libs.debug.info
import com.sup.dev.java.tools.ToolsThreads

class SIntroConnection : Screen(R.layout.screen_intro_connection){
    enum class State {
        PROGRESS, ERROR
    }

    private var loadedBackground = false

    private val vProgress: View = findViewById(R.id.vProgress)
    private val vBackground: ImageView = findViewById(R.id.vBackground)
    private val vEmptyImage: ImageView = findViewById(R.id.vEmptyImage)
    private val vMessage: TextView = findViewById(R.id.vMessage)
    private val vRetry: TextView = findViewById(R.id.vRetry)
    private val vChangeAccount: TextView = findViewById(R.id.vChangeAccount)
    private val vBackgroundInfo: LinearLayout = findViewById(R.id.vBackgroundInfo)
    private val vTitle: TextView = findViewById(R.id.vTitle)
    private val vSubtitle: TextView = findViewById(R.id.vSubtitle)
    private var feedCategories: Array<Long> = emptyArray()  //  Костыль. Загрузка настроек может перезаписать выбранные пользователем фильтры

    init {
        activityRootBackground = ToolsResources.getColorAttr(R.attr.colorPrimary)
        disableNavigation()
        isBackStackAllowed = false

        vMessage.visibility = View.INVISIBLE
        vRetry.visibility = View.INVISIBLE
        vChangeAccount.visibility = View.INVISIBLE
        vRetry.text = ToolsResources.s(R.string.retry)
        vChangeAccount.text = ToolsResources.s(R.string.app_change_account)
        vRetry.setOnClickListener { sendLoginRequest() }
        vChangeAccount.setOnClickListener { Navigator.set(SIntroAccount()) }
        vMessage.text = ToolsResources.s(R.string.connection_error)

        App.activity().updateMessagesCount()
        ControllerActivities.clear()
        App.activity().updateNotificationsCount()
        App.activity().resetStacks()
        ControllerHoliday.onAppStart()

        loadBackgroundImageData()
        sendLoginRequest()
    }

    override fun onBackPressed(): Boolean {
        App.activity().finish()
        return true
    }


    private fun sendLoginRequest() {
        val account = ControllerApi.getLastAccount()
        if (account.id == 0L) {
            ControllerChats.clearMessagesCount()

            val auth = FirebaseAuth.getInstance()
            auth.useAppLanguage()
            setState(State.PROGRESS)
            if (auth.currentUser != null) {
                auth.currentUser?.getIdToken(true)
                    ?.addOnSuccessListener {
                        if (auth.currentUser?.isEmailVerified == true) {
                            sendLoginRequestNow()
                        } else {
                            Navigator.replace(SIntroEmailVerify(false))
                        }
                    }
                    ?.addOnFailureListener {
                        ToolsToast.show(it.localizedMessage ?: it.message)
                        setState(State.ERROR)
                    }
            } else {
                sendLoginRequestNow()
            }

            if (ControllerNotifications.token.isEmpty())
                ToolsThreads.timerMain(1000, 1000 * 60L, {
                    if (ControllerNotifications.token.isNotEmpty()) {
                        it.unsubscribe()
                        sendNotificationsToken()
                    }
                })

        } else {
            vEmptyImage.visibility = View.INVISIBLE
            vProgress.visibility = View.INVISIBLE
            ControllerApi.setCurrentAccount(account, ControllerApi.getLastHasSubscribes(), ControllerApi.getLastProtadmins())
            ToolsThreads.main(3000) { sendLoginRequestBackground() }
            toFeed()
        }
    }

    private fun sendNotificationsToken() {
        RAccountsAddNotificationsToken(ControllerNotifications.token)
                .send(api)
    }

    private fun sendLoginRequestNow() {
        setState(State.PROGRESS)
        val languageId = ControllerApi.getLanguageId()
        RAccountsLogin(
                ControllerNotifications.token, languageId,
                ControllerTranslate.getSavedHash(languageId),
                ControllerTranslate.getSavedHash(API.LANGUAGE_EN)
        ).onComplete { r ->
            ControllerABParams.set(r.ABParams)
            ControllerApi.setVersion(r.version, r.supported)
            if (ControllerApi.isUnsupportedVersion()) {
                Navigator.set(SUpdate())
            } else {
                ControllerApi.setCurrentAccount(r.account ?: Account(), r.hasSubscribes, r.protoadmins)

                if (r.account == null) {
                    if (ControllerGoogleAuth.containsToken() && ControllerApiLogin.isLoginGoogle())
                        registration_google()
                    else {
                        ToolsToast.show(t(API_TRANSLATE.error_cant_login))
                        Navigator.set(SIntroAccount())
                    }
                } else {
                    val lastSettingsAccountId = ControllerSettings.getLastSettingsAccountId()
                    ControllerTranslate.addMap(r.translate_language_id, r.translate_map, r.translateMapHash)
                    ControllerTranslate.addMap(API.LANGUAGE_EN, r.translate_map_eng, r.translateMapHashEng)
                    ControllerSettings.setSettings(r.account!!.id, r.settings)
                    ControllerApi.setServerTime(r.serverTime)
                    ToolsThreads.main(1000) { loadInfo() }
                    if (lastSettingsAccountId == r.account!!.id) {
                        toFeed()
                    } else {
                        App.activity().recreate()
                        Navigator.set(SIntro())
                    }
                }

            }
        }.onError {
            setState(State.ERROR)
        }.send(api)
    }


    private fun sendLoginRequestBackground() {
        val languageId = ControllerApi.getLanguageId()
        RAccountsLogin(
                ControllerNotifications.token, languageId,
                ControllerTranslate.getSavedHash(languageId),
                ControllerTranslate.getSavedHash(API.LANGUAGE_EN)
        ).onComplete { r ->
            ControllerABParams.set(r.ABParams)
            ControllerApi.setVersion(r.version, r.supported)
            if (ControllerApi.isUnsupportedVersion()) {
                ControllerApi.setCurrentAccount(Account(), r.hasSubscribes, r.protoadmins)
                Navigator.set(SUpdate())
            } else {
                ControllerApi.setCurrentAccount(r.account ?: Account(), r.hasSubscribes, r.protoadmins)

                if (r.account == null) {
                    Navigator.set(SIntro())
                } else {
                    ControllerTranslate.addMap(r.translate_language_id, r.translate_map, r.translateMapHash)
                    ControllerTranslate.addMap(API.LANGUAGE_EN, r.translate_map_eng, r.translateMapHashEng)
                    ControllerSettings.setSettings(r.account!!.id, r.settings)
                    if (feedCategories.isNotEmpty()) ControllerSettings.feedCategories = feedCategories
                    ControllerApi.setServerTime(r.serverTime)
                    ToolsThreads.main(1000) { loadInfo() }
                }
            }
        }.send(api)
    }

    private fun toFeed() {
        ToolsThreads.main(true) {
            if (!App.activity().parseStartAction()) {
                SCampfireHello.showIfNeed {
                    feedCategories = ControllerSettings.feedCategories
                    App.activity().toMainScreen()
                }
            }
        }
    }

    private fun registration_google() {

        ToolsThreads.thread {
            var imgBytes: ByteArray? = null
            try {
                val photoUrl = ControllerGoogleAuth.getGooglePhotoUrl() ?: throw RuntimeException("photoUrl is null")
                val photo = ToolsBitmap.getFromURL(photoUrl) ?: throw RuntimeException("photo is null")
                val bitmap = ToolsBitmap.resize(photo, API.ACCOUNT_IMG_SIDE)
                imgBytes = ToolsBitmap.toBytes(bitmap, API.ACCOUNT_IMG_WEIGHT)
            } catch (e: Exception) {
                err(e)
            }

            RAccountsRegistration(ControllerApi.getLanguageId(), imgBytes)
                    .onComplete { Navigator.set(SIntro()) }
                    .onNetworkError { ToolsToast.show(t(API_TRANSLATE.error_network)) }
                    .onFinish { sendLoginRequest() }
                    .send(api)
        }
    }

    fun setState(state: State) {
        vRetry.visibility = if(state == State.ERROR) View.VISIBLE else View.GONE
        vChangeAccount.visibility = if(state == State.ERROR) View.VISIBLE else View.GONE
        vMessage.visibility = if(state == State.ERROR) View.VISIBLE else View.GONE

        vBackground.visibility = if(state == State.ERROR) View.GONE else View.VISIBLE
        vBackgroundInfo.visibility = if(state == State.ERROR) View.GONE else View.VISIBLE

        if (state == State.ERROR) {
            vProgress.visibility = View.GONE
            vEmptyImage.visibility = View.VISIBLE
            vProgress.visibility = View.VISIBLE
            ImageLoader.load(API_RESOURCES.IMAGE_BACKGROUND_20).noHolder().into(vEmptyImage)
        }

        if (state == State.PROGRESS && !loadedBackground) {
            vProgress.visibility = View.GONE
        }
    }

    fun loadInfo(tryCount: Int = 3) {
        if(ControllerApiLogin.isLoginNone()) return
        RAccountsGetInfo(ControllerApi.getLanguageId())
                .onComplete { r ->
                    ControllerApi.setApiInfo(r.apiInfo)
                    ControllerApi.setFandomsKarma(r.fandomsIds, r.languagesIds, r.karmaCounts)
                    ControllerApi.setFandomsViceroy(r.viceroyFandomsIds, r.viceroyLanguagesIds)
                    ControllerActivities.setRelayRacesCount(r.activitiesCount)
                    ControllerNotifications.setNewNotifications(r.notifications)
                    ControllerChats.clearMessagesCount()
                    for (i in r.chatMessagesCountTags) ControllerChats.setMessages(MChatMessagesPool(i, true).setCount(1))
                }
                .onError {
                    if (tryCount > 0)
                        ToolsThreads.main(3000) { loadInfo(tryCount - 1) }
                }
                .send(api)
    }

    companion object {
        private const val LAST_UPDATE_TIME = "SIntroConnection.bg.update"
        private const val LAST_UPDATE_DATA = "SIntroConnection.bg.data"
    }

    private fun loadBackgroundImageData() {
        val lastUpdate = ToolsStorage.getLong(LAST_UPDATE_TIME, 0L)
        // if the last update is more than 1 day old
        if (lastUpdate <= System.currentTimeMillis() - 1000 * 3600) {
            // refresh in the background
            RProjectGetLoadingPictures()
                .onComplete { r ->
                    info("refreshed loading background image info")
                    ToolsStorage.put(LAST_UPDATE_TIME, System.currentTimeMillis())
                    ToolsStorage.put(LAST_UPDATE_DATA, r.pictures)
                }
                .onError { err ->
                    err.printStackTrace()
                }
                .send(api)
        }

        val data = ToolsStorage.getJsonParsables(LAST_UPDATE_DATA, RProjectGetLoadingPictures.LoadingPicture::class)
            ?: arrayOf()
        val activeBackground = data.find { it.isActive() } ?: return

        val title = ControllerTranslate.getMyMap()?.get(activeBackground.titleTranslation)?.text
            ?: activeBackground.titleTranslation
        val subtitle = ControllerTranslate.getMyMap()?.get(activeBackground.subtitleTranslation)?.text
            ?: activeBackground.subtitleTranslation

        val imageLink = ImageLoader.load(activeBackground.imageId)
        ImageLoader.load(
            link = imageLink,
            vImage = vBackground,
            onLoadedBitmap = {
                vBackgroundInfo.visibility = View.VISIBLE
                vTitle.text = title
                vSubtitle.text = subtitle
                vEmptyImage.visibility = View.GONE
                vProgress.visibility = View.GONE
                loadedBackground = true
            }
        )
    }
}
