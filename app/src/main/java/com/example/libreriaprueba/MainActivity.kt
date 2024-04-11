package com.example.libreriaprueba

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.asLiveData
import com.example.libreriamm.MMCore
import com.example.libreriamm.entity.Device
import com.example.libreriamm.entity.DispositivoSensor
import com.example.libreriamm.entity.Model
import com.example.libreriamm.entity.Movement
import com.example.libreriamm.entity.Position
import com.example.libreriamm.entity.Version
import com.example.libreriamm.sensor.ConnectionState
import com.example.libreriaprueba.ui.theme.LibreriaPruebaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext


@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalGetImage
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    Log.d("Sensores", "START")
    val context = LocalContext.current
    val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    val reves = Model(fldSName="Reves",
        fldSDescription="",
        fldNDuration=2,
        fldBPublico=1,
        fkTipo=1,
        id=228,
        fkOwner=57,
        fldSUrl=null,
        fldDTimeCreateTime="2023-06-05T18:51:12",
        fldSStatus=2,
        fldNProgress=null,
        movements=listOf(
            Movement(
                fldSLabel="Reves",
                fldSDescription="Reves",
                id=393,
                fkOwner=228,
                fldDTimeCreateTime="2023-06-05T18:51:12"),
            Movement(
                fldSLabel="Other",
                fldSDescription="Other",
                id=394,
                fkOwner=228,
                fldDTimeCreateTime="2023-06-05T18:51:12")
        ),
        devices=listOf(
            Device(
                    fldNNumberDevice=1,
                    id=45,
                    fkPosition=1,
                    fkOwner=228,
                    position= Position(
                        fldSName="Right hand",
                        fldSDescription="1 - Mano derecha",
                        id=1))),
        versions=listOf(
            Version(
                fldFAccuracy= 0.9782609f,
                fldNEpoch=500,
                fldFLoss= 0.027077304f,
                fldSOptimizer="SGD",
                fldFLearningRate=0.0045f,
                id=539,
                fkOwner=228,
                fldDTimeCreateTime="2024-03-18T07:50:54"
            )
        ),
        dispositivos=listOf(
            DispositivoSensor(
                id=45,
                fkPosicion=1,
                fkSensor=1,
                fkOwner=228),
            DispositivoSensor(
                id=46,
                fkPosicion=1,
                fkSensor=2,
                fkOwner=228),
            DispositivoSensor(
                id=47,
                fkPosicion=1,
                fkSensor=3,
                fkOwner=228),
            DispositivoSensor(
                id=48,
                fkPosicion=1,
                fkSensor=4,
                fkOwner=228),
            DispositivoSensor(
                id=49,
                fkPosicion=1,
                fkSensor=5,
                fkOwner=228),
            DispositivoSensor(
                id=50,
                fkPosicion=1,
                fkSensor=6,
                fkOwner=228)),
        tuyo=0)
    val mmCore = MMCore(context = context, coroutineContext = coroutineContext)
    mmCore.setmodels(listOf(reves))
    Log.d("TipoSensores", "${mmCore.getTipoSensores()}")
    Log.d("LabelsModels", "${mmCore.getLabels(0)}")
    mmCore.onConnectionChange().asLiveData().observeForever{
        if (it != null) {
            when(it.second){
                ConnectionState.DISCONNECTED -> status = 0
                ConnectionState.CONNECTING -> status = 1
                ConnectionState.CONNECTED -> {
                    status = 2
                    val tipo = mmCore.getSensorType(it.first)
                    Log.d("TipoSensor", tipo.name)
                }
                ConnectionState.DISCONNECTING -> status = 1
                ConnectionState.FAILED -> status = 0
            }
        }
    }
    LibreriaPruebaTheme {
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
        }) {
            Icon(painter = painterResource(id = R.drawable.pikku), contentDescription = null, tint = if (status==0) Color.Black else if (status==1) Color.Yellow else Color.Green)
        }
    }
}