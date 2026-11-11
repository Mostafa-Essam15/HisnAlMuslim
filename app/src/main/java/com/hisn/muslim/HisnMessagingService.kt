package com.hisn.muslim

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * إشعارات فورية من لوحة Firebase (Messaging ← Topic = updates).
 * لما التطبيق في الخلفية النظام بيعرض الإشعار بنفسه؛ هنا بنعرضه لما التطبيق مفتوح،
 * وبنفحص التحديث عشان يظهر كارت «تحديث جديد» في الرئيسية.
 */
class HisnMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        Notif.ensureChannels(this)
        val title = msg.notification?.title ?: msg.data["title"] ?: "حصن المسلم"
        val body = msg.notification?.body ?: msg.data["body"] ?: return
        Notif.notify(this, Notif.ID_UPDATE, Notif.builder(this, Notif.CH_REMIND, title, body, body).build())
        Updater.check(this)
    }

    companion object {
        const val TOPIC = "updates"

        /** اشتراك الجهاز في موضوع التحديثات (آمن لو اتكرر) */
        fun subscribe(ctx: android.content.Context) {
            // على أجهزة بدون خدمات Google (هواوي الجديدة) ما بنلمسش Firebase خالص، والتحديث بيوصل بالفحص الدوري
            if (!LocationUpdater.hasGms(ctx)) return
            try {
                val fm = FirebaseMessaging.getInstance()
                fm.isAutoInitEnabled = true
                fm.subscribeToTopic(TOPIC)
            } catch (_: Throwable) {}
        }
    }
}
