package cat.dam.andy.firebasestorage_compose

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.dam.andy.firebasestorage_compose.ui.theme.FirebaseStorage_composeTheme
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask


class MainActivity : ComponentActivity() {
    private val context: Context by lazy { this }
    private var uriPhotoImage: Uri? = null
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var launcherTakePicture: ActivityResultLauncher<Intent>
    private var firebaseStorage: FirebaseStorage? = null
    private var mAuth: FirebaseAuth? = null
    private var idDevice: String? = null

    //    by mutableStateOf<ActivityResultLauncher<Intent>?>(null)
    private val launcherGallery = rememberLauncher { handleGalleryResult(it) }
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        launcherTakePicture = rememberLauncherTakePicture(launcherTakePictureCallback)
        initCloudDatabase()
        setContent {
            FirebaseStorage_composeTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser: FirebaseUser? = mAuth?.getCurrentUser()
        // updateUI(currentUser);
    }

    override fun onDestroy() {
        super.onDestroy()
        firebaseStorage = null
        mAuth = null
    }

    // Funcions del Compose que utilitzen ActivityResultLauncher per gestionar els resultats
    private fun rememberLauncher(onResult: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult(), onResult)
    }

    private fun rememberLauncherTakePicture(onResult: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return rememberLauncher(onResult)
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            DisplayImage()
            Button(
                onClick = {
                    handleTakePictureButtonClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Take Picture")
            }

            Button(
                onClick = {
                    handleGalleryButtonClick(launcherGallery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Open Gallery")
            }

            Button(
                onClick = {
                    handleCloudStorageClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Save to cloud")
            }
        }
    }

    private @Composable
    fun DisplayImage() {
        val imageUri = viewModel.imageUri.value
        //Utilitza llibreria Coil
        if (imageUri != null) {
            val painter = rememberAsyncImagePainter(model = imageUri)
            Image(
                painter = painter,
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp)
            )
        } else {
            // Mostra una imatge predeterminada o gestiona l'absència d'imatge
            // en funció de les teves necessitats.
        }
    }

    private fun handleGalleryButtonClick(launcherGallery: ActivityResultLauncher<Intent>) {
        handleMediaAccessGallery(launcherGallery)
    }

    private fun handleTakePictureButtonClick() {
        permissionManager
            .request(Permissions.PermCamImgSave)
            .rationale(
                description = "Permissions needed to take and save pictures",
                title = "Camera Access and storage Permission"
            )
            .checkAndRequestPermission { isGranted ->
                if (isGranted) {
                    getPictureImage()
                } else {
                    Toast.makeText(
                        context,
                        "Camera permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun handleCloudStorageClick() {
        val imageUri = viewModel.imageUri.value
        if (imageUri == null) {
            Toast.makeText(
                this,
                resources.getString(R.string.no_image_selected),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val cloudFilePath: String = idDevice + "_" + imageUri.lastPathSegment
            // TODO:
            // Encourage to disable anonymous acces (only enable autorizated users) on permissions of Firebase/Storage
            val firebaseStorage = FirebaseStorage.getInstance()
            val storageRef = firebaseStorage.reference
            val uploadeRef = storageRef.child(cloudFilePath)
            imageUri.let {
                uploadeRef.putFile(it).addOnFailureListener { exception: java.lang.Exception? ->
                        println(resources.getString(R.string.save_to_cloud_error))
                    }
                        .addOnSuccessListener { taskSnapshot: UploadTask.TaskSnapshot? ->
                            println(
                                resources.getString(R.string.save_to_cloud_done)
                            )
                        }
            }
            Toast.makeText(
                this,
                resources.getString(R.string.save_to_cloud_done),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getPictureImage() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "Image")
            put(MediaStore.Images.Media.DESCRIPTION, "From your Camera")
        }
        uriPhotoImage =
            contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uriPhotoImage)
        }
        // Utilitza el launcherTakePicture ja inicialitzat
        launcherTakePicture.launch(cameraIntent)
    }

    private fun handleMediaAccessGallery(launcherGallery: ActivityResultLauncher<Intent>) {
        permissionManager
            .request(Permissions.PermImagePick)
            .rationale(
                description = "Permission needed to access media",
                title = "Media Access Permission"
            )
            .checkAndRequestPermission { isGranted ->
                if (isGranted) {
                    launcherGallery.launch(getGalleryIntent())
                } else {
                    Toast.makeText(
                        context,
                        "Gallery permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun handleGalleryResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val pickedImageUri = data.data
                viewModel.imageUri.value = pickedImageUri
            }
        } else {
            Toast.makeText(context, "Cancelled...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getGalleryIntent(): Intent {
        val intent: Intent = if (apiRequiresScopePermissions()) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_PICK)
        }

        try {
            if (!apiRequiresScopePermissions()) {
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                intent.action = Intent.ACTION_GET_CONTENT
            }

            if (intent.resolveActivity(packageManager) == null) {
                Toast.makeText(
                    context, "El seu dispositiu no permet accedir a la galeria",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return intent
    }

    private val launcherTakePictureCallback = { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            // La foto s'ha capturat amb èxit i es pot afegir a la galeria aquí si és necessari.
            Toast.makeText(this, "Image captured", Toast.LENGTH_SHORT).show()
            viewModel.imageUri.value = uriPhotoImage
            if (!apiRequiresScopePermissions()) {
                refreshGallery() //refresca gallery per veure nou fitxer (OLD API)
            }
            //Intent data = result.getData(); //si volguessim només la miniatura
        } else {
            // L'usuari pot haver cancel·lat la presa de la foto o pot haver-hi altres problemes.
            Toast.makeText(
                context, getString(R.string.photo_capture_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun refreshGallery() {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = uriPhotoImage
        this.sendBroadcast(mediaScanIntent)
    }

    private fun apiRequiresScopePermissions(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(
            Build.VERSION_CODES.R
        ) >= 2
    }

    @SuppressLint("HardwareIds")
    private fun initCloudDatabase() {
        idDevice =
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        try {
            FirebaseApp.initializeApp(this@MainActivity)
        } catch (e: Exception) {
            // Potser ja s'ha inicialitzat Firebase, ignora l'excepció
            println("Error initializing FirebaseApp: ${e.message}")
        }
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance()
        // TODO:
        // Encourage to disable anonymous acces (only enable autorizated users) on permissions of Firebase/Storage
        mAuth?.signInAnonymously()?.addOnCompleteListener(this) { task ->
            if (task.isSuccessful()) {
                // Sign in success, update UI with the signed-in user's information
                println("signInAnonymously:success")
                val user: FirebaseUser? = mAuth!!.getCurrentUser()
                //                            updateUI(user);
                firebaseStorage = FirebaseStorage.getInstance()
            } else {
                // If sign in fails, display a message to the user.
                println("signInAnonymously:failure " + task.getException())
                Toast.makeText(
                    this@MainActivity, "Authentication failed.",
                    Toast.LENGTH_SHORT
                ).show()
                //                            updateUI(null);
            }
        }
    }


}