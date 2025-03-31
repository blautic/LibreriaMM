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
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asLiveData
import com.example.libreriamm.MMCore
import com.example.libreriamm.camara.BodyPart
import com.example.libreriamm.camara.ObjetLabel
import com.example.libreriamm.camara.Objeto
import com.example.libreriamm.camara.Person
import com.example.libreriamm.entity.DatoEstadistica
import com.example.libreriamm.entity.Device
import com.example.libreriamm.entity.DispositivoSensor
import com.example.libreriamm.entity.Model
import com.example.libreriamm.entity.Movement
import com.example.libreriamm.entity.ObjetoEstadistica
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
import kotlinx.coroutines.delay
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
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(listOf<Person>()) }
    var objetos by remember { mutableStateOf(listOf<Objeto>()) }
    Log.d("Sensores", "START")
    val context = LocalContext.current
    val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    val desplazamiento = Model(fldSEtiquetaPos = "Frontal", devices=listOf(Device(fldNNumberDevice=1, id=2430, fkPosition=7, fkOwner=1382, position=Position(fldSName="Chest", fldSDescription="7 - Pecho", id=7), image=null)), fkOwner=57, fkTipo=1, fldBAutoTraining=false, fldDTimeCreateTime="2024-03-19T11:34:24", fldNDuration=2, fldNProgress=null, fldSDescription="Desplazamiento a izquierda con sensor ziven en pecho", fldSImage=null, fldSName="DesplazamientoLeft", fldSStatus=2, fldSUrl=null, fldSVideo=null, id=1382, movements=listOf(Movement(fldSLabel="DesplazamientoLeft", fldSDescription="DesplazamientoLeft", id=1689, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24"), Movement(fldSLabel="Other", fldSDescription="Other", id=1690, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:34:24")), versions=listOf(Version(fldFAccuracy=0.6666667f, fldNEpoch=500, fldFLoss=0.6557031f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=540, fkOwner=1382, fldDTimeCreateTime="2024-03-19T11:39:37")), dispositivos=listOf(DispositivoSensor(fkPosicion=7, id=2430, fkSensor=1, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2431, fkSensor=2, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2432, fkSensor=3, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2433, fkSensor=4, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2434, fkSensor=5, fkOwner=1382), DispositivoSensor(fkPosicion=7, id=2435, fkSensor=6, fkOwner=1382)), imagen=null, video=null, fldBPublico=0, tuyo=0)
    val reves = Model(fldSName="Reves", fldSDescription="", fldNDuration=2, fldBPublico=1, fkTipo=1, id=228, fkOwner=57, fldSUrl=null, fldDTimeCreateTime="2023-06-05T18:51:12", fldSStatus=2, fldNProgress=null, movements=listOf( Movement( fldSLabel="Reves", fldSDescription="Reves", id=393, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12"), Movement( fldSLabel="Other", fldSDescription="Other", id=394, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12") ), devices=listOf( Device( fldNNumberDevice=1, id=45, fkPosition=1, fkOwner=228, position= Position( fldSName="Right hand", fldSDescription="1 - Mano derecha", id=1))), versions=listOf( Version( fldFAccuracy= 0.9782609f, fldNEpoch=500, fldFLoss= 0.027077304f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=539, fkOwner=228, fldDTimeCreateTime="2024-03-18T07:50:54" ) ), dispositivos=listOf( DispositivoSensor( id=45, fkPosicion=1, fkSensor=1, fkOwner=228), DispositivoSensor( id=46, fkPosicion=1, fkSensor=2, fkOwner=228), DispositivoSensor( id=47, fkPosicion=1, fkSensor=3, fkOwner=228), DispositivoSensor( id=48, fkPosicion=1, fkSensor=4, fkOwner=228), DispositivoSensor( id=49, fkPosicion=1, fkSensor=5, fkOwner=228), DispositivoSensor( id=50, fkPosicion=1, fkSensor=6, fkOwner=228)), tuyo=0)
    val stadisticsReves = listOf(
        ObjetoEstadistica(45, "AccX", listOf(
            DatoEstadistica(1, -0.05730867385864258f, 0.045468322932720184f),
            DatoEstadistica(2, -0.016988638788461685f, 0.08257433027029037f),
            DatoEstadistica(3, -0.0006815795786678791f, 0.09312688559293747f),
            DatoEstadistica(4, -0.012970367446541786f, 0.08219752460718155f),
            DatoEstadistica(5, -0.06603188067674637f, 0.0605192631483078f),
            DatoEstadistica(6, -0.10048727691173553f, 0.06234777718782425f),
            DatoEstadistica(7, -0.10395112633705139f, 0.06399910897016525f),
            DatoEstadistica(8, -0.07304605096578598f, 0.06477086246013641f),
            DatoEstadistica(9, -0.04572690278291702f, 0.05327484384179115f),
            DatoEstadistica(10, -0.051154106855392456f, 0.02650899440050125f),
            DatoEstadistica(11, -0.07566555589437485f, 0.026801180094480515f),
            DatoEstadistica(12, -0.08032980561256409f, 0.03221197426319122f),
            DatoEstadistica(13, -0.08060447126626968f, 0.046289730817079544f),
            DatoEstadistica(14, -0.10974456369876862f, 0.04377809166908264f),
            DatoEstadistica(15, -0.15414899587631226f, 0.041856151074171066f),
            DatoEstadistica(16, -0.19371624290943146f, 0.0310809426009655f),
            DatoEstadistica(17, -0.21886348724365234f, 0.02695961482822895f),
            DatoEstadistica(18, -0.25110119581222534f, 0.06883523613214493f),
            DatoEstadistica(19, -0.3590858578681946f, 0.13212817907333374f),
            DatoEstadistica(20, -0.5768354535102844f, 0.17469719052314758f),
            DatoEstadistica(21, -0.8225908279418945f, 0.14632384479045868f),
            DatoEstadistica(22, -0.9913734197616577f, 0.015813028439879417f),
            DatoEstadistica(23, -0.9915514588356018f, 0.01895975135266781f),
            DatoEstadistica(24, -0.9769636392593384f, 0.051579173654317856f),
            DatoEstadistica(25, -0.9464806914329529f, 0.058693502098321915f),
            DatoEstadistica(26, -0.8643045425415039f, 0.07015500217676163f),
            DatoEstadistica(27, -0.7434054613113403f, 0.10505401343107224f),
            DatoEstadistica(28, -0.5511693954467773f, 0.12427954375743866f),
            DatoEstadistica(29, -0.3573513925075531f, 0.11041122674942017f),
            DatoEstadistica(30, -0.18600523471832275f, 0.08267536759376526f),
            DatoEstadistica(31, -0.0638192892074585f, 0.05618399754166603f),
            DatoEstadistica(32, 0.012726218439638615f, 0.030995970591902733f),
            DatoEstadistica(33, 0.05306660011410713f, 0.013416202738881111f),
            DatoEstadistica(34, 0.08005005121231079f, 0.030758248642086983f),
            DatoEstadistica(35, 0.0957213044166565f, 0.04371511936187744f),
            DatoEstadistica(36, 0.09038056433200836f, 0.04797867685556412f),
            DatoEstadistica(37, 0.060818303376436234f, 0.03449421748518944f),
            DatoEstadistica(38, 0.003646961646154523f, 0.04087794944643974f),
            DatoEstadistica(39, -0.07363607734441757f, 0.0394858717918396f),
            DatoEstadistica(40, -0.13136692345142365f, 0.03407951816916466f)), 1),
        ObjetoEstadistica(46, "AccY", listOf(
            DatoEstadistica(1, 0.2062796950340271f, 0.11293186992406845f),
            DatoEstadistica(2, 0.1916409730911255f, 0.1061900183558464f),
            DatoEstadistica(3, 0.19984537363052368f, 0.062021005898714066f),
            DatoEstadistica(4, 0.22100995481014252f, 0.028702374547719955f),
            DatoEstadistica(5, 0.2298654168844223f, 0.05197208374738693f),
            DatoEstadistica(6, 0.2210404872894287f, 0.05522194132208824f),
            DatoEstadistica(7, 0.20039470493793488f, 0.03187818080186844f),
            DatoEstadistica(8, 0.190567746758461f, 0.027706656605005264f),
            DatoEstadistica(9, 0.17549669742584229f, 0.055844664573669434f),
            DatoEstadistica(10, 0.15996785461902618f, 0.07874862104654312f),
            DatoEstadistica(11, 0.17674286663532257f, 0.12887096405029297f),
            DatoEstadistica(12, 0.18132063746452332f, 0.1549619883298874f),
            DatoEstadistica(13, 0.2219306081533432f, 0.13422444462776184f),
            DatoEstadistica(14, 0.2640970051288605f, 0.10093081742525101f),
            DatoEstadistica(15, 0.2678660452365875f, 0.059764083474874496f),
            DatoEstadistica(16, 0.2621387243270874f, 0.09829413890838623f),
            DatoEstadistica(17, 0.1987517923116684f, 0.2830505967140198f),
            DatoEstadistica(18, 0.125949889421463f, 0.29999974370002747f),
            DatoEstadistica(19, 0.04297515004873276f, 0.18771067261695862f),
            DatoEstadistica(20, 0.08310190588235855f, 0.1645960509777069f),
            DatoEstadistica(21, 0.05032501742243767f, 0.1393120288848877f),
            DatoEstadistica(22, 0.006958220154047012f, 0.09528976678848267f),
            DatoEstadistica(23, 0.047232478857040405f, 0.12051356583833694f),
            DatoEstadistica(24, 0.11475468426942825f, 0.16563989222049713f),
            DatoEstadistica(25, 0.06419568508863449f, 0.12104224413633347f),
            DatoEstadistica(26, 0.04229865223169327f, 0.10818000882863998f),
            DatoEstadistica(27, 0.056810203939676285f, 0.10025656968355179f),
            DatoEstadistica(28, 0.11403241008520126f, 0.07063285261392593f),
            DatoEstadistica(29, 0.16191086173057556f, 0.06905637681484222f),
            DatoEstadistica(30, 0.183482363820076f, 0.053035035729408264f),
            DatoEstadistica(31, 0.19669179618358612f, 0.048755306750535965f),
            DatoEstadistica(32, 0.21234270930290222f, 0.030584169551730156f),
            DatoEstadistica(33, 0.20867539942264557f, 0.0228497963398695f),
            DatoEstadistica(34, 0.21072521805763245f, 0.016534091904759407f),
            DatoEstadistica(35, 0.21672210097312927f, 0.03111879900097847f),
            DatoEstadistica(36, 0.21911780536174774f, 0.03671939671039581f),
            DatoEstadistica(37, 0.22472813725471497f, 0.05987013503909111f),
            DatoEstadistica(38, 0.21073031425476074f, 0.070716492831707f),
            DatoEstadistica(39, 0.21283608675003052f, 0.06796838343143463f),
            DatoEstadistica(40, 0.1904049813747406f, 0.07012572139501572f)), 1),
        ObjetoEstadistica(47, "AccZ", listOf(
            DatoEstadistica(1, 0.14547155797481537f, 0.05323091894388199f),
            DatoEstadistica(2, 0.14428134262561798f, 0.046146392822265625f),
            DatoEstadistica(3, 0.15730765461921692f, 0.0521865151822567f),
            DatoEstadistica(4, 0.16436760127544403f, 0.04421539604663849f),
            DatoEstadistica(5, 0.17010000348091125f, 0.0503058098256588f),
            DatoEstadistica(6, 0.16491185128688812f, 0.07532449066638947f),
            DatoEstadistica(7, 0.14435763657093048f, 0.0822187066078186f),
            DatoEstadistica(8, 0.11553799360990524f, 0.05673665553331375f),
            DatoEstadistica(9, 0.08808150142431259f, 0.04553516209125519f),
            DatoEstadistica(10, 0.07853429764509201f, 0.04039720818400383f),
            DatoEstadistica(11, 0.061611782759428024f, 0.044939469546079636f),
            DatoEstadistica(12, 0.018850265070796013f, 0.052920617163181305f),
            DatoEstadistica(13, -0.008611306548118591f, 0.056654222309589386f),
            DatoEstadistica(14, -0.0199336726218462f, 0.06829778850078583f),
            DatoEstadistica(15, -0.03656117245554924f, 0.046710215508937836f),
            DatoEstadistica(16, -0.059134699404239655f, 0.03389584273099899f),
            DatoEstadistica(17, -0.09444969892501831f, 0.027796590700745583f),
            DatoEstadistica(18, -0.10763878375291824f, 0.0245361290872097f),
            DatoEstadistica(19, -0.11269976943731308f, 0.044069889932870865f),
            DatoEstadistica(20, -0.119271419942379f, 0.07940399646759033f),
            DatoEstadistica(21, -0.23184911906719208f, 0.13615089654922485f),
            DatoEstadistica(22, -0.4118930697441101f, 0.14450062811374664f),
            DatoEstadistica(23, -0.5775526165962219f, 0.042663414031267166f),
            DatoEstadistica(24, -0.6003550291061401f, 0.12118716537952423f),
            DatoEstadistica(25, -0.4518163502216339f, 0.14499664306640625f),
            DatoEstadistica(26, -0.29157382249832153f, 0.08571302145719528f),
            DatoEstadistica(27, -0.20879746973514557f, 0.027782300487160683f),
            DatoEstadistica(28, -0.19366028904914856f, 0.025172678753733635f),
            DatoEstadistica(29, -0.1749575287103653f, 0.03340255841612816f),
            DatoEstadistica(30, -0.1437777876853943f, 0.0440165251493454f),
            DatoEstadistica(31, -0.10537023842334747f, 0.054219894111156464f),
            DatoEstadistica(32, -0.060680970549583435f, 0.059640977531671524f),
            DatoEstadistica(33, -0.011581774801015854f, 0.05110016465187073f),
            DatoEstadistica(34, 0.03894161805510521f, 0.04308047890663147f),
            DatoEstadistica(35, 0.0872625932097435f, 0.03355509042739868f),
            DatoEstadistica(36, 0.13186030089855194f, 0.022780627012252808f),
            DatoEstadistica(37, 0.16462701559066772f, 0.012996052391827106f),
            DatoEstadistica(38, 0.17921994626522064f, 0.02294330857694149f),
            DatoEstadistica(39, 0.1750084012746811f, 0.04248100891709328f),
            DatoEstadistica(40, 0.16522212326526642f, 0.047513697296381f)), 1),
        ObjetoEstadistica(48, "GyrX", listOf(
            DatoEstadistica(1, -0.0004323458706494421f, 0.01767190732061863f),
            DatoEstadistica(2, -0.001871801563538611f, 0.024713754653930664f),
            DatoEstadistica(3, 0.002370271598920226f, 0.030878188088536263f),
            DatoEstadistica(4, 0.0023194067180156708f, 0.010268333368003368f),
            DatoEstadistica(5, -0.028316089883446693f, 0.06293935328722f),
            DatoEstadistica(6, 0.0013580741360783577f, 0.028402645140886307f),
            DatoEstadistica(7, 0.04708497226238251f, 0.14122320711612701f),
            DatoEstadistica(8, 0.016001872718334198f, 0.04361557960510254f),
            DatoEstadistica(9, -0.019577624276280403f, 0.04987272620201111f),
            DatoEstadistica(10, -0.006607256829738617f, 0.06521178036928177f),
            DatoEstadistica(11, 0.0063631110824644566f, 0.09164261072874069f),
            DatoEstadistica(12, 0.011179947294294834f, 0.10251985490322113f),
            DatoEstadistica(13, 0.028438163921236992f, 0.08539346605539322f),
            DatoEstadistica(14, 0.011861524544656277f, 0.09304225444793701f),
            DatoEstadistica(15, 0.014913378283381462f, 0.10567237436771393f),
            DatoEstadistica(16, -0.0011393543099984527f, 0.10146477818489075f),
            DatoEstadistica(17, 0.01828567311167717f, 0.05695754662156105f),
            DatoEstadistica(18, 0.12253181636333466f, 0.12607651948928833f),
            DatoEstadistica(19, 0.1590217798948288f, 0.1968451589345932f),
            DatoEstadistica(20, 0.059221167117357254f, 0.17867924273014069f),
            DatoEstadistica(21, 0.10822881013154984f, 0.20650002360343933f),
            DatoEstadistica(22, 0.020126953721046448f, 0.1740218549966812f),
            DatoEstadistica(23, 0.0794193372130394f, 0.15172168612480164f),
            DatoEstadistica(24, 0.11239966750144958f, 0.1266324371099472f),
            DatoEstadistica(25, 0.09904273599386215f, 0.1833101361989975f),
            DatoEstadistica(26, -0.030309967696666718f, 0.0684710294008255f),
            DatoEstadistica(27, 0.005508589558303356f, 0.10973042249679565f),
            DatoEstadistica(28, 0.027395449578762054f, 0.0730503648519516f),
            DatoEstadistica(29, -0.0006459749420173466f, 0.07512194663286209f),
            DatoEstadistica(30, 0.007044689264148474f, 0.04790087789297104f),
            DatoEstadistica(31, 0.007019256707280874f, 0.05306587740778923f),
            DatoEstadistica(32, 0.006520788185298443f, 0.05183885991573334f),
            DatoEstadistica(33, 0.01063570100814104f, 0.05830750986933708f),
            DatoEstadistica(34, 0.014180932193994522f, 0.052711762487888336f),
            DatoEstadistica(35, 0.015600045211613178f, 0.05649062991142273f),
            DatoEstadistica(36, 0.004058960825204849f, 0.055763356387615204f),
            DatoEstadistica(37, -0.0157526396214962f, 0.06638499349355698f),
            DatoEstadistica(38, -0.004064049571752548f, 0.07981669902801514f),
            DatoEstadistica(39, 0.0069327885285019875f, 0.08706451207399368f),
            DatoEstadistica(40, 0.005905331578105688f, 0.06047670170664787f)), 1),
        ObjetoEstadistica(49, "GyrY", listOf(
            DatoEstadistica(1, 0.05500452592968941f, 0.033344805240631104f),
            DatoEstadistica(2, 0.05164240300655365f, 0.022971076890826225f),
            DatoEstadistica(3, 0.043178606778383255f, 0.02553090825676918f),
            DatoEstadistica(4, 0.071825310587883f, 0.024601450189948082f),
            DatoEstadistica(5, 0.095767080783844f, 0.02259310521185398f),
            DatoEstadistica(6, 0.1034475713968277f, 0.01750084012746811f),
            DatoEstadistica(7, 0.10797448456287384f, 0.02746875397861004f),
            DatoEstadistica(8, 0.10758282989263535f, 0.02595905028283596f),
            DatoEstadistica(9, 0.09796950221061707f, 0.021878525614738464f),
            DatoEstadistica(10, 0.10261339694261551f, 0.031518660485744476f),
            DatoEstadistica(11, 0.12251655757427216f, 0.04269442707300186f),
            DatoEstadistica(12, 0.15354879200458527f, 0.07102295756340027f),
            DatoEstadistica(13, 0.17309081554412842f, 0.10074538737535477f),
            DatoEstadistica(14, 0.18576107919216156f, 0.12250587344169617f),
            DatoEstadistica(15, 0.1991078406572342f, 0.13480722904205322f),
            DatoEstadistica(16, 0.15615811944007874f, 0.11081250011920929f),
            DatoEstadistica(17, 0.08652506023645401f, 0.1616094410419464f),
            DatoEstadistica(18, 0.047598697245121f, 0.2499968409538269f),
            DatoEstadistica(19, 0.01229896117001772f, 0.2766428589820862f),
            DatoEstadistica(20, -0.017405716702342033f, 0.29523274302482605f),
            DatoEstadistica(21, 0.005701874848455191f, 0.3316136598587036f),
            DatoEstadistica(22, 0.027049565687775612f, 0.33883923292160034f),
            DatoEstadistica(23, 0.017827896401286125f, 0.3052963614463806f),
            DatoEstadistica(24, -0.029582612216472626f, 0.24589471518993378f),
            DatoEstadistica(25, -0.10812707990407944f, 0.15111000835895538f),
            DatoEstadistica(26, -0.16885383427143097f, 0.12362854182720184f),
            DatoEstadistica(27, -0.1835383176803589f, 0.1670960783958435f),
            DatoEstadistica(28, -0.1669464111328125f, 0.1935354322195053f),
            DatoEstadistica(29, -0.1265399158000946f, 0.19573552906513214f),
            DatoEstadistica(30, -0.08017721772193909f, 0.18000783026218414f),
            DatoEstadistica(31, -0.03271584212779999f, 0.1615319848060608f),
            DatoEstadistica(32, 0.013748587109148502f, 0.14845813810825348f),
            DatoEstadistica(33, 0.05967894569039345f, 0.13462144136428833f),
            DatoEstadistica(34, 0.09960733354091644f, 0.11233212798833847f),
            DatoEstadistica(35, 0.12823368608951569f, 0.09347239136695862f),
            DatoEstadistica(36, 0.14438815414905548f, 0.07191363722085953f),
            DatoEstadistica(37, 0.1498509645462036f, 0.05285013094544411f),
            DatoEstadistica(38, 0.1576738804578781f, 0.03455140069127083f),
            DatoEstadistica(39, 0.1600339710712433f, 0.03538001328706741f),
            DatoEstadistica(40, 0.1662851870059967f, 0.04051869735121727f)), 1),
        ObjetoEstadistica(50, "GyrZ", listOf(
            DatoEstadistica(1, 0.00256864121183753f, 0.018284665420651436f),
            DatoEstadistica(2, 0.003514715237542987f, 0.015391603112220764f),
            DatoEstadistica(3, 0.0006307160365395248f, 0.014333530329167843f),
            DatoEstadistica(4, 0.01743115484714508f, 0.018375322222709656f),
            DatoEstadistica(5, 0.03258868306875229f, 0.03393840789794922f),
            DatoEstadistica(6, 0.044109418988227844f, 0.048156529664993286f),
            DatoEstadistica(7, 0.0473799854516983f, 0.05219317600131035f),
            DatoEstadistica(8, 0.044200971722602844f, 0.04589873552322388f),
            DatoEstadistica(9, 0.044180627912282944f, 0.04746897891163826f),
            DatoEstadistica(10, 0.05976032838225365f, 0.0652298852801323f),
            DatoEstadistica(11, 0.08046205341815948f, 0.08785651624202728f),
            DatoEstadistica(12, 0.09897661209106445f, 0.10455288738012314f),
            DatoEstadistica(13, 0.10819319635629654f, 0.10994572937488556f),
            DatoEstadistica(14, 0.10188096016645432f, 0.10032856464385986f),
            DatoEstadistica(15, 0.08888007700443268f, 0.09453222155570984f),
            DatoEstadistica(16, 0.06909390538930893f, 0.11019036173820496f),
            DatoEstadistica(17, 0.05323445424437523f, 0.11844737082719803f),
            DatoEstadistica(18, 0.037639494985342026f, 0.10858903080224991f),
            DatoEstadistica(19, 0.008631651289761066f, 0.11646350473165512f),
            DatoEstadistica(20, -0.018631547689437866f, 0.14199236035346985f),
            DatoEstadistica(21, -0.07642851769924164f, 0.16274045407772064f),
            DatoEstadistica(22, -0.12508520483970642f, 0.16885921359062195f),
            DatoEstadistica(23, -0.13890498876571655f, 0.16831715404987335f),
            DatoEstadistica(24, -0.12891018390655518f, 0.14663653075695038f),
            DatoEstadistica(25, -0.10238451510667801f, 0.15382851660251617f),
            DatoEstadistica(26, -0.08271533250808716f, 0.15715597569942474f),
            DatoEstadistica(27, -0.08205918222665787f, 0.12482369691133499f),
            DatoEstadistica(28, -0.087308369576931f, 0.09668634086847305f),
            DatoEstadistica(29, -0.08221686631441116f, 0.062170159071683884f),
            DatoEstadistica(30, -0.06999420374631882f, 0.047080736607313156f),
            DatoEstadistica(31, -0.062354400753974915f, 0.04536482319235802f),
            DatoEstadistica(32, -0.05715099349617958f, 0.04680351912975311f),
            DatoEstadistica(33, -0.04838709905743599f, 0.04038986936211586f),
            DatoEstadistica(34, -0.04277677834033966f, 0.034197431057691574f),
            DatoEstadistica(35, -0.03621021285653114f, 0.028487885370850563f),
            DatoEstadistica(36, -0.026678264141082764f, 0.020867323502898216f),
            DatoEstadistica(37, -0.014109724201261997f, 0.01626896858215332f),
            DatoEstadistica(38, 0.0005239009042270482f, 0.016693545505404472f),
            DatoEstadistica(39, 0.006469924468547106f, 0.016397161409258842f),
            DatoEstadistica(40, 0.01271604560315609f, 0.018886910751461983f)), 1)
    )
    val zancadaDer = Model(fldSName = "Zancada derecha", fldSDescription = "", fldNDuration = 2, fldBPublico = 1, fldSVideo = null, fldSImage = null, fkTipo = 2, fldBRegresivo = 0, id = 1455, fkOwner = 44, fldSUrl = null, fldDTimeCreateTime = "2024-06-24T16:39:56", fldSStatus = 2, fldNProgress = null, movements = listOf( Movement(fldSLabel = "Zancada derecha", fldSDescription = "Zancada derecha", id = 1815, fkOwner = 1455, fldDTimeCreateTime = "2024-06-24T16:39:56" ), Movement(fldSLabel = "Other", fldSDescription = "Other", id = 1816, fkOwner = 1455, fldDTimeCreateTime = "2024-06-24T16:39:56" ) ), devices = listOf( Device(fldNNumberDevice = 1, id = 4634, fkPosition = 0, fkOwner = 1455, position = Position(fldSName = "Puntos", fldSDescription = "0 - Ninguna", id = 0 ) ) ), versions = listOf(), dispositivos = listOf(DispositivoSensor(id = 4634, fkPosicion = 0, fkSensor = 7, fkOwner = 1455 ), DispositivoSensor(id = 4635, fkPosicion = 0, fkSensor = 8, fkOwner = 1455 ), DispositivoSensor(id = 4636, fkPosicion = 0, fkSensor = 9, fkOwner = 1455 ), DispositivoSensor(id = 4637, fkPosicion = 0, fkSensor = 10, fkOwner = 1455 ), DispositivoSensor(id = 4638, fkPosicion = 0, fkSensor = 11, fkOwner = 1455 ), DispositivoSensor(id = 4639, fkPosicion = 0, fkSensor = 12, fkOwner = 1455 ), DispositivoSensor(id = 4640, fkPosicion = 0, fkSensor = 13, fkOwner = 1455 ), DispositivoSensor(id = 4641, fkPosicion = 0, fkSensor = 14, fkOwner = 1455 ), DispositivoSensor(id = 4642, fkPosicion = 0, fkSensor = 15, fkOwner = 1455 ), DispositivoSensor(id = 4643, fkPosicion = 0, fkSensor = 16, fkOwner = 1455 ), DispositivoSensor(id = 4644, fkPosicion = 0, fkSensor = 17, fkOwner = 1455 ), DispositivoSensor(id = 4645, fkPosicion = 0, fkSensor = 18, fkOwner = 1455 ), DispositivoSensor(id = 4646, fkPosicion = 0, fkSensor = 19, fkOwner = 1455 ), DispositivoSensor(id = 4647, fkPosicion = 0, fkSensor = 20, fkOwner = 1455 ), DispositivoSensor(id = 4648, fkPosicion = 0, fkSensor = 21, fkOwner = 1455 ), DispositivoSensor(id = 4649, fkPosicion = 0, fkSensor = 22, fkOwner = 1455 ), DispositivoSensor(id = 4650, fkPosicion = 0, fkSensor = 23, fkOwner = 1455 ), DispositivoSensor(id = 4651, fkPosicion = 0, fkSensor = 29, fkOwner = 1455 ), DispositivoSensor(id = 4652, fkPosicion = 0, fkSensor = 30, fkOwner = 1455 ), DispositivoSensor(id = 4653, fkPosicion = 0, fkSensor = 31, fkOwner = 1455 ), DispositivoSensor(id = 4654, fkPosicion = 0, fkSensor = 32, fkOwner = 1455 ), DispositivoSensor(id = 4655, fkPosicion = 0, fkSensor = 33, fkOwner = 1455 ), DispositivoSensor(id = 4656, fkPosicion = 0, fkSensor = 34, fkOwner = 1455 ), DispositivoSensor(id = 4657, fkPosicion = 0, fkSensor = 35, fkOwner = 1455 ), DispositivoSensor(id = 4658, fkPosicion = 0, fkSensor = 36, fkOwner = 1455 ), DispositivoSensor(id = 4659, fkPosicion = 0, fkSensor = 37, fkOwner = 1455 ), DispositivoSensor(id = 4660, fkPosicion = 0, fkSensor = 38, fkOwner = 1455 ), DispositivoSensor(id = 4661, fkPosicion = 0, fkSensor = 39, fkOwner = 1455 ), DispositivoSensor(id = 4662, fkPosicion = 0, fkSensor = 40, fkOwner = 1455 ), DispositivoSensor(id = 4663, fkPosicion = 0, fkSensor = 41, fkOwner = 1455 ), DispositivoSensor(id = 4664, fkPosicion = 0, fkSensor = 42, fkOwner = 1455 ), DispositivoSensor(id = 4665, fkPosicion = 0, fkSensor = 43, fkOwner = 1455 ), DispositivoSensor(id = 4666, fkPosicion = 0, fkSensor = 44, fkOwner = 1455 ), DispositivoSensor(id = 4667, fkPosicion = 0, fkSensor = 45, fkOwner = 1455 ), DispositivoSensor(id = 4668, fkPosicion = 0, fkSensor = 84, fkOwner = 1455 ), DispositivoSensor(id = 4669, fkPosicion = 0, fkSensor = 85, fkOwner = 1455 ) ), tuyo = 0)
    val pruebaZiven = Model(
        fldSName="prueba ziven",
        fldSDescription="",
        fldNDuration=2,
        fldBPublico=1,
        fkTipo=1,
        id=1725,
        fkOwner=57,
        fldSUrl=null,
        fldDTimeCreateTime="2023-06-05T18:51:12",
        fldSStatus=2,
        fldNProgress=null,
        movements=listOf(
            Movement( fldSLabel="prueba ziven", fldSDescription="prueba ziven", id=393, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12"),
            Movement( fldSLabel="Other", fldSDescription="Other", id=394, fkOwner=228, fldDTimeCreateTime="2023-06-05T18:51:12") ),
        devices=listOf(
            Device( fldNNumberDevice=1, id=45, fkPosition=1, fkOwner=228, position= Position( fldSName="Right hand", fldSDescription="1 - Mano derecha", id=1))),
        versions=listOf(
            Version( fldFAccuracy= 0.9782609f, fldNEpoch=500, fldFLoss= 0.027077304f, fldSOptimizer="SGD", fldFLearningRate=0.0045f, id=539, fkOwner=228, fldDTimeCreateTime="2024-03-18T07:50:54" ) ),
        dispositivos=listOf(
            DispositivoSensor( id=13985, fkPosicion=5, fkSensor=1, fkOwner=1725),
            DispositivoSensor( id=13985, fkPosicion=5, fkSensor=2, fkOwner=1725),
            DispositivoSensor( id=13987, fkPosicion=5, fkSensor=3, fkOwner=1725),
            DispositivoSensor( id=13988, fkPosicion=5, fkSensor=4, fkOwner=1725),
            DispositivoSensor( id=13989, fkPosicion=5, fkSensor=5, fkOwner=1725),
            DispositivoSensor( id=13990, fkPosicion=5, fkSensor=6, fkOwner=1725),
            DispositivoSensor( id=13991, fkPosicion=5, fkSensor=24, fkOwner=1725),
            DispositivoSensor( id=13992, fkPosicion=5, fkSensor=25, fkOwner=1725),
            DispositivoSensor( id=13993, fkPosicion=5, fkSensor=26, fkOwner=1725),
            DispositivoSensor( id=13994, fkPosicion=5, fkSensor=27, fkOwner=1725)),
        tuyo=0)
    val mmCore by remember { mutableStateOf(MMCore(context = context, coroutineContext = coroutineContext))}
    var respuesta by remember { mutableFloatStateOf(0.5f) }
    var duracion by remember { mutableFloatStateOf(0f) }
    var hrs = remember { mutableStateListOf<Float>() }
    var cacheDetector by remember { mutableStateOf<List<Pair<Person, List<Objeto>>>>(listOf()) }
    var reproduciendoCache by remember { mutableStateOf(0) }
    var personCache by remember { mutableStateOf<Person?>(null) }
    var raqueta by remember { mutableStateOf<Objeto?>(null) }
    var ecgs = remember { mutableStateListOf<Float>() }
    Log.d("TipoSensores", "${mmCore.getTipoSensores()}")
    Log.d("LabelsModels", "${mmCore.getLabels(0)}")

    suspend fun actualizarCache(){
        Log.d("CacheDetector", "${cacheDetector.size}")
        if(cacheDetector.isNotEmpty()){
            if(cacheDetector.size > reproduciendoCache){
                personCache = cacheDetector[reproduciendoCache].first
                if(cacheDetector[reproduciendoCache].second.isNotEmpty()) {
                    raqueta = cacheDetector[reproduciendoCache].second[0]
                }else{
                    raqueta = null
                }
                reproduciendoCache += 1
            }
            if(reproduciendoCache >= cacheDetector.size){
                reproduciendoCache = 0
            }
        }else{
            personCache = null
            raqueta = null
            reproduciendoCache = 0
        }
        delay(100)
        actualizarCache()
    }

    LaunchedEffect(Unit){
        mmCore.setFrontCamera(camSelector != CamSelector.Back)
        scope.launch {
            imageAnalyzer.update { imageProxy ->
                mmCore.updateImage(imageProxy)
                imageProxy.close()
            }
        }
        mmCore.setmodels(listOf(zancadaDer))
        //mmCore.startMotionDetector()
        //mmCore.setObjetsLabels(listOf(ObjetLabel.CHAIR, ObjetLabel.TV, ObjetLabel.CUP))
        //mmCore.addObjetLabel(ObjetLabel.TENNIS_RACKET)
        //mmCore.setExplicabilidad(0, stadisticsReves)
        //mmCore.addGenericSensor(4, listOf(TypeData.AccX, TypeData.AccY, TypeData.AccZ, TypeData.AI))
        //mmCore.addGenericSensor(7, listOf(TypeData.HR))
        scope.launch {
            actualizarCache()
        }

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
    mmCore.getMotionDetectorPosicion().asLiveData().observeForever {
        if(it != null){
            error = it
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
    mmCore.onPersonsDetected().asLiveData().observeForever{
        if(it != null){
            result = it
        }
    }
    mmCore.onObjectDetected().asLiveData().observeForever {
        if(it != null){
            objetos = it
        }
    }
    mmCore.onCacheDetector().asLiveData().observeForever {
        if(it.isNotEmpty()){
            cacheDetector = it
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
                        objetos = objetos,
                        personaCache = personCache,
                        raqueta = raqueta
                    )
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
                    mmCore.startMotionDetector()
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
                            mmCore.startConnectDevice(4)
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
                Text(text = error)
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
                    //mmCore.getCacheInference()
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
fun PersonDetectionResultNew(bitmap: Bitmap, persons: List<Person>, objetos: List<Objeto>, camSelector: CamSelector, personaCache: Person?, raqueta: Objeto?) {

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
            Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE),

            Pair(BodyPart.RIGHT_WRIST, BodyPart.RIGHT_PULGAR),
            Pair(BodyPart.RIGHT_WRIST, BodyPart.RIGHT_INDICE),
            Pair(BodyPart.RIGHT_WRIST, BodyPart.RIGHT_PINKY),
            Pair(BodyPart.RIGHT_INDICE, BodyPart.RIGHT_PINKY),
            Pair(BodyPart.RIGHT_ANKLE, BodyPart.RIGHT_TALON),
            Pair(BodyPart.RIGHT_ANKLE, BodyPart.RIGHT_PIE),
            Pair(BodyPart.RIGHT_TALON, BodyPart.RIGHT_PIE),

            Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_PULGAR),
            Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_INDICE),
            Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_PINKY),
            Pair(BodyPart.LEFT_INDICE, BodyPart.LEFT_PINKY),
            Pair(BodyPart.LEFT_ANKLE, BodyPart.LEFT_TALON),
            Pair(BodyPart.LEFT_ANKLE, BodyPart.LEFT_PIE),
            Pair(BodyPart.LEFT_TALON, BodyPart.LEFT_PIE)

        )
        Log.d("PERSONAS", "Personas detectadas: ${persons.size}")
        persons.forEach{ person ->
            person.keyPoints.forEach { keyPoint ->
                /*
                    El ancho de la imagen son 480
                    Para voltear los puntos hay que restarle a 480 la coordenada en X original
                 */

                if (camSelector == CamSelector.Front) {
                    drawCircle(
                        color = Color.Red,
                        center = Offset(
                            (480 - keyPoint.coordinate.x) * widthFactor,
                            keyPoint.coordinate.y * heightFactor
                        ),
                        radius = 10f
                    )
                } else {
                    drawCircle(
                        color = Color.Red,
                        center = Offset(
                            keyPoint.coordinate.x * widthFactor,
                            keyPoint.coordinate.y * heightFactor
                        ),
                        radius = 10f
                    )
                }

            }
            connections.forEach { (startPart, endPart) ->
                val startPoint = person.keyPoints.find { it.bodyPart == startPart }
                val endPoint = person.keyPoints.find { it.bodyPart == endPart }
                if (startPoint != null && endPoint != null) {
                    if (camSelector == CamSelector.Front) {
                        drawLine(
                            color = Color.Red,
                            start = Offset(
                                (480 - startPoint.coordinate.x) * widthFactor,
                                startPoint.coordinate.y * heightFactor
                            ),
                            end = Offset(
                                (480 - endPoint.coordinate.x) * widthFactor,
                                endPoint.coordinate.y * heightFactor
                            ),
                            strokeWidth = 5f
                        )
                    } else {
                        drawLine(
                            color = Color.Red,
                            start = Offset(
                                startPoint.coordinate.x * widthFactor,
                                startPoint.coordinate.y * heightFactor
                            ),
                            end = Offset(
                                endPoint.coordinate.x * widthFactor,
                                endPoint.coordinate.y * heightFactor
                            ),
                            strokeWidth = 5f
                        )
                    }
                }
            }
        }
        objetos.forEach { it1 ->
            Log.d("OBJETOS", "${it1}")
            if(camSelector == CamSelector.Front){
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset((480 - it1.point.left) * widthFactor, it1.point.top * heightFactor),
                    size = Size(it1.point.width() * -1f * widthFactor, it1.point.height() * heightFactor),
                    alpha = 0.3f
                )
            }else{
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(it1.point.left * widthFactor, it1.point.top * heightFactor),
                    size = Size(it1.point.width() * widthFactor, it1.point.height() * heightFactor),
                    alpha = 0.3f
                )
            }
        }
        if(personaCache != null){
            connections.forEach { (startPart, endPart) ->
                val startPoint = personaCache.keyPoints.find { it.bodyPart == startPart }
                val endPoint = personaCache.keyPoints.find { it.bodyPart == endPart }
                if (startPoint != null && endPoint != null) {
                    if (camSelector == CamSelector.Back) {
                        drawLine(
                            color = Color.Blue,
                            start = Offset(
                                (480 - startPoint.coordinate.x) * widthFactor,
                                startPoint.coordinate.y * heightFactor
                            ),
                            end = Offset(
                                (480 - endPoint.coordinate.x) * widthFactor,
                                endPoint.coordinate.y * heightFactor
                            ),
                            strokeWidth = 5f
                        )
                    } else {
                        drawLine(
                            color = Color.Blue,
                            start = Offset(
                                startPoint.coordinate.x * widthFactor,
                                startPoint.coordinate.y * heightFactor
                            ),
                            end = Offset(
                                endPoint.coordinate.x * widthFactor,
                                endPoint.coordinate.y * heightFactor
                            ),
                            strokeWidth = 5f
                        )
                    }
                }
            }
        }
        if(raqueta != null){
            if(camSelector == CamSelector.Front){
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset((480 - raqueta.point.left) * widthFactor, raqueta.point.top * heightFactor),
                    size = Size(raqueta.point.width() * -1f * widthFactor, raqueta.point.height() * heightFactor),
                )
            }else{
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(raqueta.point.left * widthFactor, raqueta.point.top * heightFactor),
                    size = Size(raqueta.point.width() * widthFactor, raqueta.point.height() * heightFactor),
                )
            }
        }
    }

}

