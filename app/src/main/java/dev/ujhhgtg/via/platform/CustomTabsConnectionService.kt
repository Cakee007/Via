package dev.ujhhgtg.via.platform

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException

/** mark.via.service.CustomTabsConnectionService and the original b.b Binder protocol. */
class CustomTabsConnectionService : Service() {
    private val sessions = mutableMapOf<IBinder, IBinder.DeathRecipient>()
    private val connection = object : Binder(), IInterface {
        init { attachInterface(this, DESCRIPTOR) }
        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
            if (code in 1..16777215) data.enforceInterface(DESCRIPTOR)
            val output = reply ?: return false
            val result: Int = when (code) {
                2 -> { data.readLong(); 1 } // warmup
                3 -> if (newSession(data.readStrongBinder())) 1 else 0
                4 -> { // mayLaunchUrl
                    data.readStrongBinder(); read(data, Uri.CREATOR); read(data, Bundle.CREATOR)
                    data.createTypedArrayList(Bundle.CREATOR); 1
                }
                5 -> { // extraCommand returns a null Bundle.
                    data.readString(); read(data, Bundle.CREATOR)
                    output.writeNoException(); output.writeInt(0); return true
                }
                6 -> { data.readStrongBinder(); read(data, Bundle.CREATOR); 0 } // updateVisuals
                7 -> { data.readStrongBinder(); read(data, Uri.CREATOR); 0 } // requestPostMessageChannel
                8 -> { data.readStrongBinder(); data.readString(); read(data, Bundle.CREATOR); -1 } // postMessage
                9 -> { // validateRelationship: original advertises support and returns true.
                    data.readStrongBinder(); data.readInt(); read(data, Uri.CREATOR); read(data, Bundle.CREATOR); 1
                }
                10 -> {
                    val binder = data.readStrongBinder(); read(data, Bundle.CREATOR)
                    if (newSession(binder)) 1 else 0
                }
                11 -> { data.readStrongBinder(); read(data, Uri.CREATOR); read(data, Bundle.CREATOR); 0 }
                12 -> { data.readStrongBinder(); read(data, Uri.CREATOR); data.readInt(); read(data, Bundle.CREATOR); 0 }
                13 -> { data.readStrongBinder(); read(data, Bundle.CREATOR); 0 }
                14 -> { data.readStrongBinder(); data.readStrongBinder(); read(data, Bundle.CREATOR); 0 }
                else -> return super.onTransact(code, data, reply, flags)
            }
            output.writeNoException()
            output.writeInt(result)
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = connection

    private fun newSession(binder: IBinder?): Boolean {
        if (binder == null) return false
        val death = IBinder.DeathRecipient { synchronized(sessions) { sessions.remove(binder) } }
        return try {
            binder.linkToDeath(death, 0)
            synchronized(sessions) { sessions.put(binder, death)?.let { binder.unlinkToDeath(it, 0) } }
            true
        } catch (_: RemoteException) { false }
    }

    override fun onDestroy() {
        synchronized(sessions) { sessions.forEach { (binder, death) -> binder.unlinkToDeath(death, 0) }; sessions.clear() }
        super.onDestroy()
    }

    private fun <T> read(parcel: Parcel, creator: Parcelable.Creator<T>): T? =
        if (parcel.readInt() == 0) null else creator.createFromParcel(parcel)

    companion object { private const val DESCRIPTOR = "android.support.customtabs.ICustomTabsService" }
}
