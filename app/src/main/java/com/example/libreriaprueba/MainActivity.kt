package com.example.libreriaprueba

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asLiveData
import com.example.libreriamm.MMCore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    var status by remember { mutableStateOf(0) }
    var status2 by remember { mutableStateOf(0) }
    Log.d("Sensores", "START")
    val context = LocalContext.current
    val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    val desplazamiento = Model(devices=listOf(Device(fldNNumberDevice=1, id=2430, fkPosition=7, fkOwner=1382, position=Position(fldSName="Chest", fldSDescription="7 - Pecho", id=7), image=null)), fkOwner=57, fkTipo=1, fldBAutoTraining=false, fldDTimeCreateTime="2024-03-19T11:34:24", fldNDuration=2, fldNProgress=null, fldSDescription="Desplazamiento a izquierda con sensor ziven en pecho", fldSImage=null, fldSName="DesplazamientoLeft", fldSStatus=2, fldSUrl=null, fldSVideo=null, id=1382, movements=listOf(Movement(fldSLabel="DesplazamientoLeft", fldSDescription="DesplazamientoLeft", id=1689, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24"), Movement(fldSLabel="Other", fldSDescription="Other", id=1690, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24")), versions=listOf(Version(fldFAccuracy=0.6666667f, fldNEpoch=500, fldFLoss=0.6557031f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=540, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:39:37")), dispositivos=listOf(DispositivoSensor(fkPosicion=7, id=2430, fkSensor=1, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2431, fkSensor=2, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2432, fkSensor=3, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2433, fkSensor=4, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2434, fkSensor=5, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2435, fkSensor=6, fkOwner=1382)), imagen=null, video=null, fldBPublico=0, tuyo=0)
    val reves = Model(fldSName="Reves", fldSDescription="", fldNDuration=2, fldBPublico=1, fkTipo=1, id=228, fkOwner=57, fldSUrl=null, fldDTimeCreateTime="2023-06-05T18:51:12", fldSStatus=2, fldNProgress=null, movements=listOf( Movement( fldSLabel="Reves", fldSDescription="Reves", id=393, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12"), Movement( fldSLabel="Other", fldSDescription="Other", id=394, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12") ), devices=listOf( Device( fldNNumberDevice=1, id=45, fkPosition=1, fkOwner=228, position= Position( fldSName="Right hand", fldSDescription="1 - Mano derecha", id=1))), versions=listOf( Version( fldFAccuracy= 0.9782609f, fldNEpoch=500, fldFLoss= 0.027077304f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=539, fkOwner=228, fldDTimeCreateTime="2024-03-18T07:50:54" ) ), dispositivos=listOf( DispositivoSensor( id=45, fkPosicion=1, fkSensor=1, fkOwner=228), DispositivoSensor( id=46, fkPosicion=1, fkSensor=2, fkOwner=228), DispositivoSensor( id=47, fkPosicion=1, fkSensor=3, fkOwner=228), DispositivoSensor( id=48, fkPosicion=1, fkSensor=4, fkOwner=228), DispositivoSensor( id=49, fkPosicion=1, fkSensor=5, fkOwner=228), DispositivoSensor( id=50, fkPosicion=1, fkSensor=6, fkOwner=228)), tuyo=0)
    val mmCore by remember { mutableStateOf(MMCore(context = context, coroutineContext = coroutineContext))}
    var respuesta by remember { mutableFloatStateOf(0.5f) }
    var duracion by remember { mutableFloatStateOf(0f) }
    mmCore.setmodels(listOf(reves))
    mmCore.addGenericSensor(7, listOf(TypeData.AccX))
    Log.d("TipoSensores", "${mmCore.getTipoSensores()}")
    Log.d("LabelsModels", "${mmCore.getLabels(0)}")
    mmCore.onMotionDetectorChange().asLiveData().observeForever{
        if (it != null){
            respuesta = it.second[0]
            Log.d("INFERENCIA", "Model: ${it.first}, Result:${it.second[0]}-${it.second[1]}")
        }
    }
    mmCore.onMotionDetectorCorrectChange().asLiveData().observeForever{
        if (it != null){
            duracion = it.second
        }
    }
    mmCore.onConnectionChange().asLiveData().observeForever{
        if (it != null) {
            when(it.first) {
                0 -> when (it.second) {
                    ConnectionState.DISCONNECTED -> {
                        status = 0
                        mmCore.startMotionDetector()
                    }
                    ConnectionState.CONNECTING -> status = 1
                    ConnectionState.CONNECTED -> {
                        status = 2
                        val tipo = mmCore.getSensorType(it.first)
                        Log.d("TipoSensor", tipo.name)
                        mmCore.startMotionDetector()
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
                    }

                    ConnectionState.DISCONNECTING -> status2 = 1
                    ConnectionState.FAILED -> status2 = 0
                }
            }
        }
    }
    LibreriaPruebaTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Bottom
        ) {
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
    }
}