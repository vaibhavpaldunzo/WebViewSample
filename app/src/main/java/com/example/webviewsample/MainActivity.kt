package com.example.webviewsample

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        val PERMISSION_REQUEST_CODE = 16545
        var READ_CONTACT_PERMISSION = 1
        const val PICK_CONTACT = 0
        const val PICK_FROM_GALLERY = 2
        const val REQUEST_GALLERY_CODE = 1002
        const val REQUEST_IMAGE_CAPTURE = 1
    }

    lateinit var locationTv: TextView
    lateinit var openContactTv: TextView
    lateinit var cameraTv: TextView
    lateinit var galleryTv: TextView
    lateinit var webviewTv: TextView
    lateinit var imageView: ImageView
    lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationTv = findViewById(R.id.locationTv)
        openContactTv = findViewById(R.id.contactTv)
        cameraTv = findViewById(R.id.cameraTv)
        galleryTv = findViewById(R.id.galleryTv)
        webviewTv = findViewById(R.id.webviewTv)
        imageView = findViewById(R.id.imageView)
        editText = findViewById(R.id.editText)


        locationTv.setOnClickListener {
            requestPermissions()
        }

        openContactTv.setOnClickListener {
            openPhoneBook()
        }

        cameraTv.setOnClickListener {
            openCamera()
        }

        galleryTv.setOnClickListener {
            startGalleryToPickImage()
        }

        webviewTv.setOnClickListener {
            startWebviewActivity(editText.text.toString())
        }
    }

    fun startWebviewActivity(url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("URL", url)
        startActivity(intent)
    }

    private fun requestPermissions() {
        val permissionsToAskArray = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(permissionsToAskArray, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            PICK_CONTACT -> {
                if (resultCode == Activity.RESULT_OK) {
                    val contactData: Uri? = data?.data
                    pickContacts(contactData)
                }
            }
            REQUEST_GALLERY_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
                    try {
                        val imageRealPath = getRealPathFromURI(this, data.data)
                        if (TextUtils.isEmpty(imageRealPath)) return

                        val photoFile = File(imageRealPath)
                        loadImage(photoFile.path, imageView)
                        Toast.makeText(this, "Path for picked image from gallery: ${photoFile.path}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun loadImage(url: String, imageView: ImageView) {
        val imgFile = File(url)
        if (imgFile.exists()) {
            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
            imageView.setImageBitmap(bmp)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) return
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    private fun openPhoneBook() {
        if (!PermissionUtils.hasPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS))) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                READ_CONTACT_PERMISSION
            )
        } else {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            startActivityForResult(intent, PICK_CONTACT)
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
                            setShowToast("You picked the phone number ${name} : ${contactInfos[0].number}")
                        } else if (contactInfos.size > 1) {
                            val contactList = mutableListOf<ContactDetails>()
                            contactInfos.forEach {
                                contactList.add(ContactDetails(it.number, name, it.contactType))
                            }
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

    private fun startGalleryToPickImage() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.v("Gallery", "We need to read storage!!")
                // Explain to the user why we need to read the contacts
            }

            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PICK_FROM_GALLERY
            )

            return
        }
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        val mimeTypes = arrayOf("image/*", "vnd.android.cursor.dir/image")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_GALLERY_CODE)
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                //postEvent(RemovePrescriptionPathEvent)
                val photoFile = createImageFile()
                //postEvent(AddPrescriptionPathEvent(photoFile))

                // Continue only if the File was successfully created
                photoFile?.let {
                    val photoURI: Uri = FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(
                        takePictureIntent,
                        REQUEST_IMAGE_CAPTURE
                    )
                }
            }
        }
    }

    @Throws(IOException::class)
    fun createImageFile(): File? {
        // Create an image file name
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File? = getAppImageFolder()

        // Save a file: path for use with ACTION_VIEW intents
//        mCurrentPhotoPath = image.getAbsolutePath();
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

    fun setShowToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

data class ContactInfo(
    val number: String,
    val contactType: String,
    var selected: Boolean
) {

    companion object {
        fun getContactTypeString(type: String): String {
            return when (type) {
                TYPE_ASSISTANT -> "ASSISTANT"
                TYPE_CALLBACK -> "CALLBACK"
                TYPE_CAR -> "CAR"
                TYPE_COMPANY_MAIN -> "COMPANY-MAIN"
                TYPE_FAX_HOME -> "FAX-HOME"
                TYPE_FAX_WORK -> "FAX-WORK"
                TYPE_HOME -> "HOME"
                TYPE_ISDN -> "ISDN"
                TYPE_MAIN -> "MAIN"
                TYPE_MMS -> "MMS"
                TYPE_MOBILE -> "MOBILE"
                TYPE_OTHER -> "OTHER"
                TYPE_OTHER_FAX -> "OTHER-FAX"
                TYPE_PAGER -> "PAGER"
                TYPE_RADIO -> "RADIO"
                TYPE_TELEX -> "TELEX"
                TYPE_TTY_TDD -> "TTY-TDD"
                TYPE_WORK -> "WORK"
                TYPE_WORK_MOBILE -> "WORK-MOBILE"
                TYPE_WORK_PAGER -> "WORK-PAGER"
                else -> "MOBILE"
            }
        }

        private const val TYPE_ASSISTANT = "19"
        private const val TYPE_CALLBACK = "8"
        private const val TYPE_CAR = "9"
        private const val TYPE_COMPANY_MAIN = "10"
        private const val TYPE_FAX_HOME = "5"
        private const val TYPE_FAX_WORK = "4"
        private const val TYPE_HOME = "1"
        private const val TYPE_ISDN = "11"
        private const val TYPE_MAIN = "12"
        private const val TYPE_MMS = "20"
        private const val TYPE_MOBILE = "2"
        private const val TYPE_OTHER = "7"
        private const val TYPE_OTHER_FAX = "13"
        private const val TYPE_PAGER = "6"
        private const val TYPE_RADIO = "14"
        private const val TYPE_TELEX = "15"
        private const val TYPE_TTY_TDD = "16"
        private const val TYPE_WORK = "3"
        private const val TYPE_WORK_MOBILE = "17"
        private const val TYPE_WORK_PAGER = "18"
    }
}

data class ContactDetails(
    val phoneNo: String,
    val name: String?,
    val type: String = ""
)

