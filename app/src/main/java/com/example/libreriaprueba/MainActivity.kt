package com.example.libreriaprueba

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.CameraController
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asLiveData
import com.example.libreriamm.MMCore
import com.example.libreriamm.camara.BodyPart
import com.example.libreriamm.camara.Person
import com.example.libreriamm.entity.Device
import com.example.libreriamm.entity.DispositivoSensor
import com.example.libreriamm.entity.Model
import com.example.libreriamm.entity.Movement
import com.example.libreriamm.entity.Position
import com.example.libreriamm.entity.Version
import com.example.libreriamm.sensor.ConnectionState
import com.example.libreriamm.sensor.TypeData
import com.example.libreriaprueba.ui.theme.LibreriaPruebaTheme
import com.google.firebase.FirebaseApp
import com.ujizin.camposer.CameraPreview
import com.ujizin.camposer.state.CamSelector
import com.ujizin.camposer.state.CaptureMode
import com.ujizin.camposer.state.ImageAnalysisBackpressureStrategy
import com.ujizin.camposer.state.ImageTargetSize
import com.ujizin.camposer.state.ImplementationMode
import com.ujizin.camposer.state.ScaleType
import com.ujizin.camposer.state.rememberCamSelector
import com.ujizin.camposer.state.rememberCameraState
import com.ujizin.camposer.state.rememberImageAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            LibreriaPruebaTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GreetingPreview()
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
fun GreetingPreview() {
    val finalBitmap = remember {
        mutableStateOf(
            Bitmap.createBitmap(
                480,
                640,
                Bitmap.Config.ARGB_8888
            )
        )
    }
    // CAMERA SETTINGS
    val cameraState = rememberCameraState()
    var camSelector by rememberCamSelector(CamSelector.Front)
    val imageAnalyzer =
        cameraState.rememberImageAnalyzer(imageAnalysisBackpressureStrategy = ImageAnalysisBackpressureStrategy.KeepOnlyLatest,
            imageAnalysisTargetSize = ImageTargetSize(
                aspectRatio = AspectRatio.RATIO_4_3,
                outputSize = CameraController.OutputSize(AspectRatio.RATIO_4_3)
            ),
            analyze = { })
    val scope = rememberCoroutineScope()
    var sample by remember { mutableIntStateOf(0) }

    var status by remember { mutableStateOf(0) }
    var status2 by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf(Person()) }
    Log.d("Sensores", "START")
    val context = LocalContext.current
    val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    val desplazamiento = Model(devices=listOf(Device(fldNNumberDevice=1, id=2430, fkPosition=7, fkOwner=1382, position=Position(fldSName="Chest", fldSDescription="7 - Pecho", id=7), image=null)), fkOwner=57, fkTipo=1, fldBAutoTraining=false, fldDTimeCreateTime="2024-03-19T11:34:24", fldNDuration=2, fldNProgress=null, fldSDescription="Desplazamiento a izquierda con sensor ziven en pecho", fldSImage=null, fldSName="DesplazamientoLeft", fldSStatus=2, fldSUrl=null, fldSVideo=null, id=1382, movements=listOf(Movement(fldSLabel="DesplazamientoLeft", fldSDescription="DesplazamientoLeft", id=1689, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24"), Movement(fldSLabel="Other", fldSDescription="Other", id=1690, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24")), versions=listOf(Version(fldFAccuracy=0.6666667f, fldNEpoch=500, fldFLoss=0.6557031f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=540, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:39:37")), dispositivos=listOf(DispositivoSensor(fkPosicion=7, id=2430, fkSensor=1, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2431, fkSensor=2, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2432, fkSensor=3, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2433, fkSensor=4, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2434, fkSensor=5, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2435, fkSensor=6, fkOwner=1382)), imagen=null, video=null, fldBPublico=0, tuyo=0)
    val reves = Model(fldSName="Reves", fldSDescription="", fldNDuration=2, fldBPublico=1, fkTipo=1, id=228, fkOwner=57, fldSUrl=null, fldDTimeCreateTime="2023-06-05T18:51:12", fldSStatus=2, fldNProgress=null, movements=listOf( Movement( fldSLabel="Reves", fldSDescription="Reves", id=393, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12"), Movement( fldSLabel="Other", fldSDescription="Other", id=394, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12") ), devices=listOf( Device( fldNNumberDevice=1, id=45, fkPosition=1, fkOwner=228, position= Position( fldSName="Right hand", fldSDescription="1 - Mano derecha", id=1))), versions=listOf( Version( fldFAccuracy= 0.9782609f, fldNEpoch=500, fldFLoss= 0.027077304f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=539, fkOwner=228, fldDTimeCreateTime="2024-03-18T07:50:54" ) ), dispositivos=listOf( DispositivoSensor( id=45, fkPosicion=1, fkSensor=1, fkOwner=228), DispositivoSensor( id=46, fkPosicion=1, fkSensor=2, fkOwner=228), DispositivoSensor( id=47, fkPosicion=1, fkSensor=3, fkOwner=228), DispositivoSensor( id=48, fkPosicion=1, fkSensor=4, fkOwner=228), DispositivoSensor( id=49, fkPosicion=1, fkSensor=5, fkOwner=228), DispositivoSensor( id=50, fkPosicion=1, fkSensor=6, fkOwner=228)), tuyo=0)
    val zancadaDer = Model(fldSName = "Zancada derecha", fldSDescription = "", fldNDuration = 2, fldBPublico = 1, fldSVideo = null, fldSImage = null, fkTipo = 2, fldBRegresivo = 0, id = 1455, fkOwner = 44, fldSUrl = null, fldDTimeCreateTime = "2024-06-24T16:39:56", fldSStatus = 2, fldNProgress = null, movements = listOf( Movement(fldSLabel = "Zancada derecha", fldSDescription = "Zancada derecha", id = 1815, fkOwner = 1455, fldDTimeCreateTime = "2024-06-24T16:39:56" ), Movement(fldSLabel = "Other", fldSDescription = "Other", id = 1816, fkOwner = 1455, fldDTimeCreateTime = "2024-06-24T16:39:56" ) ), devices = listOf( Device(fldNNumberDevice = 1, id = 4634, fkPosition = 0, fkOwner = 1455, position = Position(fldSName = "Puntos", fldSDescription = "0 - Ninguna", id = 0 ) ) ), versions = listOf(), dispositivos = listOf(DispositivoSensor(id = 4634, fkPosicion = 0, fkSensor = 7, fkOwner = 1455 ), DispositivoSensor(id = 4635, fkPosicion = 0, fkSensor = 8, fkOwner = 1455 ), DispositivoSensor(id = 4636, fkPosicion = 0, fkSensor = 9, fkOwner = 1455 ), DispositivoSensor(id = 4637, fkPosicion = 0, fkSensor = 10, fkOwner = 1455 ), DispositivoSensor(id = 4638, fkPosicion = 0, fkSensor = 11, fkOwner = 1455 ), DispositivoSensor(id = 4639, fkPosicion = 0, fkSensor = 12, fkOwner = 1455 ), DispositivoSensor(id = 4640, fkPosicion = 0, fkSensor = 13, fkOwner = 1455 ), DispositivoSensor(id = 4641, fkPosicion = 0, fkSensor = 14, fkOwner = 1455 ), DispositivoSensor(id = 4642, fkPosicion = 0, fkSensor = 15, fkOwner = 1455 ), DispositivoSensor(id = 4643, fkPosicion = 0, fkSensor = 16, fkOwner = 1455 ), DispositivoSensor(id = 4644, fkPosicion = 0, fkSensor = 17, fkOwner = 1455 ), DispositivoSensor(id = 4645, fkPosicion = 0, fkSensor = 18, fkOwner = 1455 ), DispositivoSensor(id = 4646, fkPosicion = 0, fkSensor = 19, fkOwner = 1455 ), DispositivoSensor(id = 4647, fkPosicion = 0, fkSensor = 20, fkOwner = 1455 ), DispositivoSensor(id = 4648, fkPosicion = 0, fkSensor = 21, fkOwner = 1455 ), DispositivoSensor(id = 4649, fkPosicion = 0, fkSensor = 22, fkOwner = 1455 ), DispositivoSensor(id = 4650, fkPosicion = 0, fkSensor = 23, fkOwner = 1455 ), DispositivoSensor(id = 4651, fkPosicion = 0, fkSensor = 29, fkOwner = 1455 ), DispositivoSensor(id = 4652, fkPosicion = 0, fkSensor = 30, fkOwner = 1455 ), DispositivoSensor(id = 4653, fkPosicion = 0, fkSensor = 31, fkOwner = 1455 ), DispositivoSensor(id = 4654, fkPosicion = 0, fkSensor = 32, fkOwner = 1455 ), DispositivoSensor(id = 4655, fkPosicion = 0, fkSensor = 33, fkOwner = 1455 ), DispositivoSensor(id = 4656, fkPosicion = 0, fkSensor = 34, fkOwner = 1455 ), DispositivoSensor(id = 4657, fkPosicion = 0, fkSensor = 35, fkOwner = 1455 ), DispositivoSensor(id = 4658, fkPosicion = 0, fkSensor = 36, fkOwner = 1455 ), DispositivoSensor(id = 4659, fkPosicion = 0, fkSensor = 37, fkOwner = 1455 ), DispositivoSensor(id = 4660, fkPosicion = 0, fkSensor = 38, fkOwner = 1455 ), DispositivoSensor(id = 4661, fkPosicion = 0, fkSensor = 39, fkOwner = 1455 ), DispositivoSensor(id = 4662, fkPosicion = 0, fkSensor = 40, fkOwner = 1455 ), DispositivoSensor(id = 4663, fkPosicion = 0, fkSensor = 41, fkOwner = 1455 ), DispositivoSensor(id = 4664, fkPosicion = 0, fkSensor = 42, fkOwner = 1455 ), DispositivoSensor(id = 4665, fkPosicion = 0, fkSensor = 43, fkOwner = 1455 ), DispositivoSensor(id = 4666, fkPosicion = 0, fkSensor = 44, fkOwner = 1455 ), DispositivoSensor(id = 4667, fkPosicion = 0, fkSensor = 45, fkOwner = 1455 ), DispositivoSensor(id = 4668, fkPosicion = 0, fkSensor = 84, fkOwner = 1455 ), DispositivoSensor(id = 4669, fkPosicion = 0, fkSensor = 85, fkOwner = 1455 ) ), tuyo = 0)
    val mmCore by remember { mutableStateOf(MMCore(context = context, coroutineContext = coroutineContext))}
    var respuesta by remember { mutableFloatStateOf(0.5f) }
    var duracion by remember { mutableFloatStateOf(0f) }
    var hrs = remember { mutableStateListOf<Float>() }
    var ecgs = remember { mutableStateListOf<Float>() }
    Log.d("TipoSensores", "${mmCore.getTipoSensores()}")
    Log.d("LabelsModels", "${mmCore.getLabels(0)}")
    LaunchedEffect(Unit){
        mmCore.setFrontCamera(camSelector != CamSelector.Back)
        scope.launch {
            imageAnalyzer.update { imageProxy ->
                mmCore.updateImage(imageProxy)
                Log.d("Dibujar Persona Libreria", "Imagen Enviada")
                imageProxy.close()
            }
        }
        mmCore.setmodels(listOf())
        mmCore.addGenericSensor(1, listOf(TypeData.AI))
        //mmCore.addGenericSensor(7, listOf(TypeData.HR))
    }
    mmCore.onMotionDetectorChange().asLiveData().observeForever{
        if (it != null){
            if(it.first == 0) {
                respuesta = it.second[0]
                Log.d("INFERENCIA", "Model: ${it.first}, Result:${it.second[0]}}")
            }
        }
    }
    mmCore.onMotionDetectorCorrectChange().asLiveData().observeForever{
        if (it != null){
            duracion = it.second
        }
    }
    mmCore.onPersonDetected().asLiveData().observeForever{
        if(it != null){
            result = it
        }
    }
    mmCore.onConnectionChange().asLiveData().observeForever{
        if (it != null) {
            when(it.first) {
                0 -> when (it.second) {
                    ConnectionState.DISCONNECTED -> {
                        status = 0
                    }
                    ConnectionState.CONNECTING -> status = 1
                    ConnectionState.CONNECTED -> {
                        status = 2
                        val tipo = mmCore.getSensorType(it.first)
                        mmCore.onSensorChange(1, TypeData.HR).asLiveData().observeForever{
                            Log.d("HR", "${it.first}")
                        }
                        /*mmCore.onSensorChange(1, TypeData.HR).asLiveData().observeForever{ it1 ->
                            Log.d("HR", "${it1.first}")
                            if(hrs.size > 300){
                                hrs.removeAt(0)
                            }
                            hrs.add(it1.first)
                        }
                        mmCore.onSensorChange(1, TypeData.Ecg).asLiveData().observeForever{ it1 ->
                            if(ecgs.size > 300){
                                ecgs.removeAt(0)
                            }
                            ecgs.add(it1.first)
                            Log.d("ECG", "${it1.first}")
                        }*/
                        Log.d("TipoSensor", tipo.name)
                        mmCore.enableAllCache(true)
                        //mmCore.startMotionDetectorIndex(listOf(0))
                    }

                    ConnectionState.DISCONNECTING -> status = 1
                    ConnectionState.FAILED -> status = 0
                }

                else -> when (it.second) {
                    ConnectionState.DISCONNECTED -> status2 = 0
                    ConnectionState.CONNECTING -> status2 = 1
                    ConnectionState.CONNECTED -> {
                        status2 = 2
                        val tipo = mmCore.getSensorType(it.first)
                        Log.d("TipoSensor", tipo.name)
                        mmCore.onSensorChange(1, TypeData.AccX).asLiveData().observeForever{
                            Log.d("ACC X", "${it.first}")
                        }
                        mmCore.enableAllCache(true)
                    }

                    ConnectionState.DISCONNECTING -> status2 = 1
                    ConnectionState.FAILED -> status2 = 0
                }
            }
        }
    }
    LibreriaPruebaTheme {
        Box{
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                cameraState = cameraState,
                camSelector = camSelector,
                scaleType = ScaleType.FitStart,
                imageAnalyzer = imageAnalyzer,
                isImageAnalysisEnabled = true,
                imageCaptureTargetSize = ImageTargetSize(AspectRatio.RATIO_4_3),
                implementationMode = ImplementationMode.Compatible,
                captureMode = CaptureMode.Image,
            ) {
                Box {
                    Log.d("Dibujar Persona", "Dibujando")
                    PersonDetectionResultNew(
                        bitmap = finalBitmap.value,
                        persons = result,
                        camSelector = camSelector,
                    )
                    Canvas(modifier = Modifier.wrapContentSize()){
                        drawLine(
                            color = Color.White,
                            start = Offset(100f, 100f),
                            end = Offset(100f, 400f),
                            strokeWidth = 10f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(1000f, 100f),
                            end = Offset(1000f, 400f),
                            strokeWidth = 10f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(100f, 100f),
                            end = Offset(1000f, 100f),
                            strokeWidth = 10f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(100f, 400f),
                            end = Offset(1000f, 400f),
                            strokeWidth = 10f
                        )
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(100f, 100f),
                            size = Size(900f, 300f)
                        )
                        val maxHr = 150f / (hrs.maxOfOrNull { it }?: 0f)
                        hrs.forEachIndexed{ index, valor ->
                            if(index >= 1){
                                drawLine(
                                    color = Color.White,
                                    start = Offset(100f+(3*(index-1)), 250f+(hrs[index-1]*maxHr)),
                                    end = Offset(100f+(3*index), 250f+(valor*maxHr)),
                                    strokeWidth = 5f
                                )
                            }
                        }
                        val maxEcg = 150f / (ecgs.maxOfOrNull { it }?: 0f)
                        ecgs.forEachIndexed{ index, valor ->
                            if(index >= 1){
                                drawLine(
                                    color = Color.Red,
                                    start = Offset(100f+(3*(index-1)), 250f+(ecgs[index-1]*maxEcg)),
                                    end = Offset(100f+(3*index), 250f+(valor*maxEcg)),
                                    strokeWidth = 5f
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Bottom
            ) {
                Button(onClick = {
                    mmCore.stopMotionDetector()
                }, modifier = Modifier.wrapContentHeight()) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                        contentDescription = null
                    )
                }
                Button(onClick = {
                    when (status) {
                        0 -> {
                            mmCore.startConnectDevice(0)
                            status = 1
                        }

                        1 -> {
                            mmCore.stopConnectDevice()
                            status = 0
                        }

                        2 -> {
                            mmCore.disconnectAll()
                            status = 0
                        }
                    }
                }, modifier = Modifier.wrapContentHeight()) {
                    Icon(
                        painter = painterResource(id = R.drawable.pikku),
                        contentDescription = null,
                        tint = if (status == 0) Color.Black else if (status == 1) Color.Yellow else Color.Green
                    )
                }
                Button(onClick = {
                    when (status2) {
                        0 -> {
                            mmCore.startConnectDevice(1)
                            status2 = 1
                        }

                        1 -> {
                            mmCore.stopConnectDevice()
                            status2 = 0
                        }

                        2 -> {
                            mmCore.disconnectAll()
                            status2 = 0
                        }
                    }
                }, modifier = Modifier.wrapContentHeight()) {
                    Icon(
                        painter = painterResource(id = R.drawable.pikku),
                        contentDescription = null,
                        tint = if (status2 == 0) Color.Black else if (status2 == 1) Color.Yellow else Color.Green
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "%.2f".format(duracion) + "s")
                LinearProgressIndicator(progress=respuesta, modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ){
                Button(onClick = {
                    camSelector = if(camSelector == CamSelector.Front) CamSelector.Back else CamSelector.Front
                    mmCore.setFrontCamera(camSelector == CamSelector.Front)
                }, modifier = Modifier.wrapContentHeight()) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.stat_notify_sync),
                        contentDescription = null
                    )
                }
                Button(onClick = {
                    mmCore.setmodels(List(5){zancadaDer})
                    mmCore.enableAllCache(true)
                    mmCore.startMotionDetector()
                }, modifier = Modifier.wrapContentHeight()) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun PersonDetectionResultNew(bitmap: Bitmap, persons: Person, camSelector: CamSelector) {

    Log.d("Dimensiones del bitmap", "Height: ${bitmap.height}  ||  ${bitmap.width}" )
    Log.d("Dimensiones de la pantalla", "Height: ${ LocalContext.current.resources.displayMetrics.heightPixels}  ||  ${LocalContext.current.resources.displayMetrics.widthPixels}" )


    var displayWidht = LocalContext.current.resources.displayMetrics.widthPixels
    var displayHeight = (displayWidht*4)/3

    Log.d("Dimensiones 4/3", "Height: ${ displayHeight}  ||  ${displayWidht}" )


    val heightFactor = displayHeight/bitmap.height.toFloat()
    val widthFactor = displayWidht/bitmap.width.toFloat()

    //val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Canvas(modifier = Modifier.wrapContentSize()) {

        //drawImage(imageBitmap)
        val connections = listOf(
            Pair(BodyPart.NOSE, BodyPart.LEFT_EYE),
            Pair(BodyPart.NOSE, BodyPart.RIGHT_EYE),
            Pair(BodyPart.LEFT_EYE, BodyPart.LEFT_EAR),
            Pair(BodyPart.RIGHT_EYE, BodyPart.RIGHT_EAR),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
            Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
            Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
            Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
            Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
            Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
        )

        persons.keyPoints.forEach { keyPoint ->
            /*
                El ancho de la imagen son 480
                Para voltear los puntos hay que restarle a 480 la coordenada en X original
             */

            if(camSelector == CamSelector.Front) {
                drawCircle(
                    color = Color.Red,
                    center = Offset((480 - keyPoint.coordinate.x) * widthFactor, keyPoint.coordinate.y * heightFactor),
                    radius = 10f
                )
            }else{
                drawCircle(
                    color = Color.Red,
                    center = Offset(keyPoint.coordinate.x * widthFactor, keyPoint.coordinate.y * heightFactor),
                    radius = 10f
                )
            }

        }
        connections.forEach { (startPart, endPart) ->
            val startPoint = persons.keyPoints.find { it.bodyPart == startPart }
            val endPoint = persons.keyPoints.find { it.bodyPart == endPart }
            if (startPoint != null && endPoint != null) {
                if(camSelector == CamSelector.Front) {
                    drawLine(
                        color = Color.Red,
                        start = Offset((480 - startPoint.coordinate.x) * widthFactor, startPoint.coordinate.y * heightFactor),
                        end = Offset((480 - endPoint.coordinate.x) * widthFactor, endPoint.coordinate.y * heightFactor),
                        strokeWidth = 5f
                    )
                }else{
                    drawLine(
                        color = Color.Red,
                        start = Offset(startPoint.coordinate.x * widthFactor, startPoint.coordinate.y * heightFactor),
                        end = Offset(endPoint.coordinate.x * widthFactor, endPoint.coordinate.y * heightFactor),
                        strokeWidth = 5f
                    )
                }
            }
        }
    }

}
