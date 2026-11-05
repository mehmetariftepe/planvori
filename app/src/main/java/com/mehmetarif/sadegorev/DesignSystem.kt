package com.mehmetarif.sadegorev

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val AccentColors=listOf(Color(0xFFA1B7D9),Color(0xFF9FC4FF),Color(0xFF8ED9B5),Color(0xFFC2A5F5),Color(0xFFF4ABC7))
val LightAccentColors=listOf(Color(0xFF263E65),Color(0xFF275CA2),Color(0xFF276D51),Color(0xFF754AA8),Color(0xFFA53C6B))

@Composable fun SectionLabel(text:String){
    Text(text.uppercase(java.util.Locale.forLanguageTag("tr")),style=MaterialTheme.typography.labelMedium.copy(letterSpacing=2.sp),color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=20.dp,bottom=8.dp))
}

@Composable fun SegmentedOptions(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier){
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(28.dp)).padding(5.dp)){
        labels.forEachIndexed { i,label ->
            val color by animateColorAsState(if(i==selected)MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,tween(180),label="segment")
            Box(Modifier.weight(1f).background(color,RoundedCornerShape(23.dp)).clickable{onSelect(i)}.padding(horizontal=5.dp,vertical=13.dp),contentAlignment=Alignment.Center){
                Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=if(i==selected)FontWeight.SemiBold else FontWeight.Normal,color=if(i==selected)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun AppearanceSettings(){
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{AppPreferences.prefs(context)}
    val scope=rememberCoroutineScope()
    var theme by remember{mutableIntStateOf(prefs.getInt(AppPreferences.APP_THEME,0))}
    var accent by remember{mutableIntStateOf(prefs.getInt(AppPreferences.APP_ACCENT,0))}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        SegmentedOptions(listOf("Otomatik","Açık","Koyu"),when(theme){3->0;1->1;else->2},onSelect={index->theme=listOf(3,1,0)[index];prefs.edit().putInt(AppPreferences.APP_THEME,theme).apply()})
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Renk",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            AccentColors.forEachIndexed { i,color->
                Box(Modifier.padding(start=9.dp).size(32.dp).border(if(accent==i)2.dp else 0.dp,if(accent==i)MaterialTheme.colorScheme.onSurface else Color.Transparent,CircleShape).padding(5.dp).background(if(i==0)LightAccentColors[0] else color,CircleShape).clickable{accent=i;prefs.edit().putInt(AppPreferences.APP_ACCENT,i).apply();scope.launch{com.mehmetarif.sadegorev.widget.refreshWidgets(context);com.mehmetarif.sadegorev.widget.refreshScheduleWidgets(context)}},contentAlignment=Alignment.Center){}
            }
        }
        Row(Modifier.fillMaxWidth().clickable{theme=2;prefs.edit().putInt(AppPreferences.APP_THEME,2).apply()}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Gece mavisi",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Text(if(theme==2)"Seçili" else "Alternatif görünüm",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
