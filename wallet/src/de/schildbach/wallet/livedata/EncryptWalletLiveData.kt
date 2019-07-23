package de.schildbach.wallet.livedata

import android.annotation.SuppressLint
import android.app.Application
import android.os.AsyncTask
import androidx.lifecycle.MutableLiveData
import de.schildbach.wallet.Constants
import de.schildbach.wallet.WalletApplication
import org.bitcoinj.crypto.KeyCrypterException
import org.bitcoinj.crypto.KeyCrypterScrypt
import org.bitcoinj.wallet.Wallet
import org.slf4j.LoggerFactory

class EncryptWalletLiveData(application: Application) : MutableLiveData<Resource<Wallet>>() {

    private val log = LoggerFactory.getLogger(EncryptWalletLiveData::class.java)

    private var encryptWalletTask: EncryptWalletTask? = null
    private var checkPinTask: CheckPinTask? = null

    private var scryptIterationsTarget: Int = Constants.SCRYPT_ITERATIONS_TARGET
    private var walletApplication = application as WalletApplication

    fun encrypt(password: String, scryptIterationsTarget: Int) {
        if (encryptWalletTask == null) {
            this.scryptIterationsTarget = scryptIterationsTarget
            encryptWalletTask = EncryptWalletTask()
            encryptWalletTask!!.execute(password)
        }
    }

    fun checkPin(pin: String) {
        if (checkPinTask == null) {
            checkPinTask = CheckPinTask()
            checkPinTask!!.execute(pin)
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class EncryptWalletTask : AsyncTask<String, Void, Resource<Wallet>>() {

        override fun onPreExecute() {
            value = Resource.loading(null)
        }

        override fun doInBackground(vararg args: String): Resource<Wallet> {
            val password = args[0]
            val wallet = walletApplication.wallet
            try {
                // For the new key, we create a new key crypter according to the desired parameters.
                val keyCrypter = KeyCrypterScrypt(scryptIterationsTarget)
                val newKey = keyCrypter.deriveKey(password)
                wallet.encrypt(keyCrypter, newKey)

                org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
                walletApplication.saveWalletAndFinalizeInitialization()

                log.info("wallet successfully encrypted, using key derived by new spending password (${keyCrypter.scryptParameters.n} scrypt iterations)")

                return Resource.success(wallet)
            } catch (x: KeyCrypterException) {
                return Resource.error(x.message!!, null)
            }
        }

        override fun onPostExecute(result: Resource<Wallet>) {
            value = result
            encryptWalletTask = null
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class CheckPinTask : AsyncTask<String, Void, Resource<Wallet>>() {

        override fun onPreExecute() {
            value = Resource.loading(null)
        }

        override fun doInBackground(vararg args: String): Resource<Wallet> {
            val password = args[0]
            val wallet = walletApplication.wallet
            return try {
                val key = wallet.keyCrypter!!.deriveKey(password)
                wallet.decrypt(key)
                Resource.success(wallet)
            } catch (x: KeyCrypterException) {
                Resource.error(x.message!!, null)
            }
        }

        override fun onPostExecute(result: Resource<Wallet>) {
            value = result
            checkPinTask = null
        }
    }
}