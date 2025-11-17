package com.bagboi.pokepaw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

@Composable
fun FilesSection(
    filesExpanded: Boolean,
    onFilesExpandedChange: () -> Unit,
    isLoaded: Boolean,
    onLoadRom: () -> Unit,
    onLoadEeprom: () -> Unit
) {
    Text(
        text = (if (filesExpanded) "\u25BE " else "\u25B8 ") + "Files",
        color = Color(0xFFAAAAFF),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFilesExpandedChange() }
            .padding(vertical = 10.dp)
    )

    if (filesExpanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROM",
                    color = Color(0xFFCBCBE5),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                if (isLoaded) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                    )
                } else {
                    Button(
                        onClick = onLoadRom,
                        modifier = Modifier
                            .padding(start = 8.dp)
                    ) {
                        Text("Select")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EEPROM",
                    color = Color(0xFFCBCBE5),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                if (isLoaded) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                    )
                } else {
                    Button(
                        onClick = onLoadEeprom,
                        modifier = Modifier
                            .padding(start = 8.dp)
                    ) {
                        Text("Select")
                    }
                }
            }
        }
    }
}
