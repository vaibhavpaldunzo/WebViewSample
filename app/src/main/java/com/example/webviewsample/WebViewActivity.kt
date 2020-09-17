package com.example.webviewsample

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.webviewsample.MainActivity.Companion.PICK_FROM_GALLERY
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val WEBVIEW_CALLBACK = "nativeInterfaceCallback"
    }
    var mGeoLocationRequestOrigin: String? = null
    var mGeoLocationCallback: GeolocationPermissions.Callback? = null
    var mCameraCallback: String? = null
    var mGalleryCallback: String? = null
    var mContactCallback: String? = null
    var photoURI: Uri? = null
    lateinit var myWebView: WebView
    val delay by lazy { intent.getIntExtra("DELAY", 30) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_layout)

        myWebView = findViewById(R.id.webview)
        myWebView.settings.javaScriptEnabled = true
        myWebView.settings.domStorageEnabled = true
        myWebView.settings.allowFileAccess = true
        myWebView.settings.allowFileAccessFromFileURLs = true
        myWebView.webViewClient = MyWebViewClient()
        myWebView.webChromeClient = object : WebChromeClient() {

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
                mGeoLocationRequestOrigin = null
                mGeoLocationCallback = null

                if (ContextCompat.checkSelfPermission(this@WebViewActivity, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    mGeoLocationRequestOrigin = origin
                    mGeoLocationCallback = callback
                    requestPermissions(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ), MainActivity.PERMISSION_REQUEST_CODE)
                } else {
                    callback?.invoke(origin, true, true)
                }
            }

        }
        myWebView.loadUrl(intent.getStringExtra("URL"))
        myWebView.addJavascriptInterface(WebAppInterface(this, myWebView, delay), "OnboardingInterface")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) return
        if (requestCode == MainActivity.PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.get(0) == PackageManager.PERMISSION_GRANTED) {
                mGeoLocationCallback?.invoke(mGeoLocationRequestOrigin, true, true)
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                mGeoLocationCallback?.invoke(mGeoLocationRequestOrigin, false, false)
                Toast.makeText(this, "Location permission denied!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == PICK_FROM_GALLERY) {
            Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun openCamera() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) && shouldShowRequestPermissionRationale(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Log.v("Gallery", "We need to read storage!!")
                    // Explain to the user why we need to read the contacts
                }

                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MainActivity.PICK_FROM_GALLERY
                )

                return
            }
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                //postEvent(RemovePrescriptionPathEvent)
                val photoFile = createImageFile()
                //postEvent(AddPrescriptionPathEvent(photoFile))

                // Continue only if the File was successfully created
                photoFile?.let {
                    photoURI = FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(
                        takePictureIntent,
                        MainActivity.REQUEST_IMAGE_CAPTURE
                    )
                }
            }
        }
    }

    var completeImageFileName: String = ""

    @Throws(IOException::class)
    fun createImageFile(): File? {
        // Create an image file name
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File? = getAppImageFolder()

        try {
            storageDir?.mkdirs()
        } catch (e: java.lang.Exception) {
            Log.v("WebView : ", e.message)
        }

        // Save a file: path for use with ACTION_VIEW intents
//        mCurrentPhotoPath = image.getAbsolutePath();
        completeImageFileName = imageFileName.plus(".jpeg")
        return File.createTempFile(
            imageFileName,  /* prefix */
            ".jpeg",  /* suffix */
            storageDir /* directory */
        )
    }

    private var mAppImageFolder: File? = null

    private fun folderSetup() {
        mAppImageFolder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    }

    fun getAppImageFolder(): File? {
        if (mAppImageFolder == null) {
            folderSetup()
        }
        return mAppImageFolder
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            MainActivity.PICK_CONTACT -> {
                if (resultCode == Activity.RESULT_OK) {
                    val contactData: Uri? = data?.data
                    pickContacts(contactData)
                }
            }
            MainActivity.REQUEST_GALLERY_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
                    try {
                        val imageRealPath = getRealPathFromURI(this, data.data)
                        if (TextUtils.isEmpty(imageRealPath)) return

                        val photoFile = File(imageRealPath)

                        myWebView.evaluateJavascript("javascript:$mGalleryCallback(\"${photoFile.path}\")", null)
                        Toast.makeText(this, "Path for picked image from gallery: ${photoFile.path}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            MainActivity.REQUEST_IMAGE_CAPTURE -> {
                var path = "/storage/emulated/0" + photoURI?.path?.removePrefix("/dunzo_images")
                var base64String = imageToBase64String(path).replace("\n", "")
                val response = ResponseObjectPhoto(
                    response_type = "PHOTO",
                    data = Photo(name = completeImageFileName, base_64_string = base64String),
                    web_identifier = currentWebIdentifier
                )
                myWebView.evaluateJavascript("javascript:$WEBVIEW_CALLBACK('${Gson().toJson(response)}')", null)
            }
        }
    }

    fun pickContacts(contactData: Uri?) {
        if (contactData == null) {
            return
        }
        val contactInfos: MutableList<ContactInfo> = ArrayList()
        val cursor: Cursor? = contentResolver.query(contactData, null, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    var duplicityString = ""
                    val hasPhone =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                    val contactId =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    if (hasPhone == "1") {
                        val phones: Cursor? = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                            null,
                            null
                        )
                        phones?.use {
                            if (it.moveToFirst()) {
                                do {
                                    val number =
                                        it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                            .replace("[-() ]".toRegex(), "")
                                    var type: String? = null
                                    try {
                                        type =
                                            it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                                    } catch (e: java.lang.Exception) {
                                    }
                                    if (type != null) {
                                        val check = "$number$$$type"
                                        if (!duplicityString.contains(check)) {
                                            duplicityString += "$check  "
                                            contactInfos.add(
                                                ContactInfo(
                                                    number,
                                                    ContactInfo.getContactTypeString(type),
                                                    false
                                                )
                                            )
                                        }
                                    }
                                } while (it.moveToNext())
                            }
                        }
                        //Do something with number
                        if (contactInfos.size == 1) {
                            val contactList = listOf(ContactDetails(contactInfos[0].number, name))
                            //setContactDetails(contactList)

                            val response = ResponseObjectContact(
                                response_type = "CONTACT",
                                data = Contact(
                                    contact_name = name,
                                    contact_list = contactInfos.map { it.number }
                                ),
                                web_identifier = currentWebIdentifier
                            )

                            myWebView.evaluateJavascript("javascript:$WEBVIEW_CALLBACK('${Gson().toJson(response)}')", null)
                            //setShowToast("You picked the phone number ${name} : ${contactInfos[0].number}")
                        } else if (contactInfos.size > 1) {
                            val contactList = mutableListOf<ContactDetails>()
                            contactInfos.forEach {
                                contactList.add(ContactDetails(it.number, name, it.contactType))
                            }
                            val response = ResponseObjectContact(
                                response_type = "CONTACT",
                                data = Contact(
                                    contact_name = name,
                                    contact_list = contactInfos.map { it.number }
                                ),
                                web_identifier = currentWebIdentifier
                            )

                            myWebView.evaluateJavascript("javascript:$WEBVIEW_CALLBACK('${Gson().toJson(response)}')", null)
                            //setContactDetails(contactList)
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor.close()
            }
        } else {
            setShowToast("This contact has no phone number")
        }

    }

    fun setShowToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    fun getRealPathFromURI(context: Activity, contentUri: Uri?): String? {
        var imagePath: String? = null
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor =
            context.managedQuery(contentUri, proj, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                imagePath = cursor.getString(column_index)
            }
        }
        return imagePath
    }

    fun startGalleryToPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) && shouldShowRequestPermissionRationale(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Log.v("Gallery", "We need to read storage!!")
                    // Explain to the user why we need to read the contacts
                }

                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MainActivity.PICK_FROM_GALLERY
                )

                return
            }
        }
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        val mimeTypes = arrayOf("image/*", "vnd.android.cursor.dir/image")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(Intent.createChooser(intent, "Select File"),
            MainActivity.REQUEST_GALLERY_CODE
        )
    }

    fun openPhoneBook() {
        if (!PermissionUtils.hasPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS))) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                MainActivity.READ_CONTACT_PERMISSION
            )
        } else {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            startActivityForResult(intent, MainActivity.PICK_CONTACT)
        }
    }

    var currentWebIdentifier: String? = null

    fun setWebIdentifier(id: String?) {
        currentWebIdentifier = id
    }

    fun imageToBase64String(path: String?): String{
        //encode image to base64 string
        val baos = ByteArrayOutputStream()
        val bitmap: Bitmap = BitmapFactory.decodeFile(path)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageBytes: ByteArray = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    fun showEventToast(eventName: String, eventMeta: Map<*,*>) {
        Toast.makeText(this, "Following Event got triggered -> $eventName", Toast.LENGTH_LONG).show()
    }
}

/** Instantiate the interface and set the context  */
class WebAppInterface(private val mContext: Context, private val mWebView: WebView, private val delay: Int) {

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun showToast(toast: String): Boolean {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        return false
    }

    @JavascriptInterface
    fun showToast2(toast: String, callback: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun changeTitleAsync(
        callback: String
    ): Boolean {
        Handler().postDelayed({
            mWebView.post {
                mWebView.evaluateJavascript("javascript:$callback(\"POC completed!!!!\")", null)
            }
        }, 2000)
        return true
    }

    @JavascriptInterface
    fun clickPic(callback: String) {
        (mContext as WebViewActivity).openCamera()
        mContext.mCameraCallback = callback
    }

    @JavascriptInterface
    fun galleryPic(callback: String) {
        (mContext as WebViewActivity).startGalleryToPickImage()
        mContext.mGalleryCallback = callback
    }

    @JavascriptInterface
    fun openContacts(callback: String) {
        (mContext as WebViewActivity).openPhoneBook()
        mContext.mContactCallback = callback
    }

    @JavascriptInterface
    fun execute(jsonInput: String) {
        val data = Gson().fromJson(jsonInput, RequestObject::class.java)
        if (data.parameters?.containsKey("web_identifier")!!) {
            (mContext as WebViewActivity).setWebIdentifier(data.parameters?.get("web_identifier") as String)
        }
        mContext as WebViewActivity
        when(data.request_type) {
            "PHOTO" -> {
                mContext.openCamera()
            }
            "CONTACT" -> {
                mContext.openPhoneBook()
            }
            "GET_JWT" -> {
                mWebView.post {
                    val response = ResponseObjectJwt(
                        response_type = "GET_JWT",
                        data = Jwt(jwt_token = "JWT eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1dWlkIjoiN2I1MjY4OGMtMzc5YS00MzRiLThkY2EtOGEwNWU2Y2UxMWVkIiwiaXNzIjoiZXNwcmVzc29AZHVuem8uaW4iLCJuYW1lIjoiVmFpYmhhdiBQYWwiLCJleHAiOjE2MDA4NTM5MTMsImlhdCI6MTYwMDMzNTUxMywic2VjcmV0X2tleSI6Ijk5OTgzYjRjLWE0Y2YtNDI1NS05MGVhLTM3ZDgwZDg5NDIzOSIsImQiOnsic2VjcmV0X2tleSI6Ijk5OTgzYjRjLWE0Y2YtNDI1NS05MGVhLTM3ZDgwZDg5NDIzOSIsInVpZCI6IjdiNTI2ODhjLTM3OWEtNDM0Yi04ZGNhLThhMDVlNmNlMTFlZCJ9fQ.MBY7r_iRovuAWDXBWIbaY7bWReyUYfxDFwkxoZk8kD8"),
                        web_identifier = mContext.currentWebIdentifier
                    )
                    mWebView.evaluateJavascript("javascript:${WebViewActivity.WEBVIEW_CALLBACK}('${Gson().toJson(response)}')", null)
                }
            }
            "REFRESH_JWT" -> {
                AsyncTask.execute {
                    Thread.sleep(delay * 1000L)
                    val response = ResponseObjectJwt(
                        response_type = "GET_JWT",
                        data = Jwt(jwt_token = "JWT eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1dWlkIjoiN2I1MjY4OGMtMzc5YS00MzRiLThkY2EtOGEwNWU2Y2UxMWVkIiwiaXNzIjoiZXNwcmVzc29AZHVuem8uaW4iLCJuYW1lIjoiVmFpYmhhdiBQYWwiLCJleHAiOjE2MDA4NTI4MDUsImlhdCI6MTYwMDMzNDQwNSwic2VjcmV0X2tleSI6ImVlMWFkMDI4LTcyMjctNDNjMy04MzAwLTYwMmU5Y2E2N2FiZSIsImQiOnsic2VjcmV0X2tleSI6ImVlMWFkMDI4LTcyMjctNDNjMy04MzAwLTYwMmU5Y2E2N2FiZSIsInVpZCI6IjdiNTI2ODhjLTM3OWEtNDM0Yi04ZGNhLThhMDVlNmNlMTFlZCJ9fQ.NgKWvUNFrrUJEw5sFJBPI01mQva3yDUf7WI9fu5Pvwo"),
                        web_identifier = mContext.currentWebIdentifier
                    )
                    mWebView.post {
                        mWebView.evaluateJavascript("javascript:${WebViewActivity.WEBVIEW_CALLBACK}('${Gson().toJson(response)}')", null)
                    }
                }
            }
            "SEND_EVENT" -> {
                data.parameters["event_name"]?.let { eventName ->
                    if (data.parameters["event_meta"] is Map<*, *>) {
                        mContext.showEventToast(eventName = eventName as String, eventMeta = data.parameters["event_meta"] as Map<*, *>)
                    }
                }
            }
        }
    }
}


data class RequestObject(
    val request_type: String,
    val parameters: Map<String, *>?
)

data class ResponseObjectPhoto(
    val response_type: String,
    val data: Photo,
    val web_identifier: String?
)

data class ResponseObjectContact(
    val response_type: String,
    val data: Contact,
    val web_identifier: String?
)

data class ResponseObjectJwt(
    val response_type: String,
    val data: Jwt,
    val web_identifier: String?
)

data class Photo(
    val name: String,
    val base_64_string: String
)

data class Contact(
    val contact_name: String,
    val contact_list: List<String>
)

data class Jwt(
    val jwt_token: String
)

class MyWebViewClient: WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        val script = "javascript:callbackFunc('Data from App!!');"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view?.evaluateJavascript("javascript: storeJwtToken(\"My JWT token\");", null)
        }
        else {
            view?.loadUrl(script);
        }
    }
}
