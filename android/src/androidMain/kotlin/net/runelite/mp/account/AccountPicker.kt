package net.runelite.mp.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the picker hands back to the host so it can seed the launcher env. Exactly one of
 * [credentials] / [credentialsFile] is set: signed-in Jagex accounts produce the `JX_*`
 * map in memory, imported files are read at boot by
 * [net.runelite.mp.RuneLiteLauncher.seedLauncherCredentials].
 */
internal class AccountSelection(
    val credentials: Map<String, String>? = null,
    val credentialsFile: java.io.File? = null,
)


/**
 * The pre-boot Compose screen that picks what to launch with. Sits in front of
 * [net.runelite.mp.RuneLiteLauncher]; tapping an account calls [onSelect] and the host
 * begins the normal RL boot flow with that account's `JX_*` values seeded into the env.
 *
 * Hardened build: the only account source is an imported `credentials.properties`
 * produced by desktop RuneLite through the Jagex Launcher. The in-app sign-in, which
 * reused the official OSRS Android app's OAuth client, has been removed.
 */
@Composable
internal fun AccountPicker(onSelect: (AccountSelection) -> Unit)
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imported by remember { mutableStateOf<List<AccountStore.Account>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun refreshList()
    {
        imported = AccountStore.list(context)
    }

    LaunchedEffect(Unit) { refreshList() }

    // SAF document picker. Returns a content:// URI; we read the bytes ourselves so we
    // can validate the file *before* it lands in our app-private dir.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Importing…"
            val displayHint = displayNameFromUri(context, uri)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null)
            {
                status = "Couldn't read the selected file."
                return@launch
            }
            val newId = withContext(Dispatchers.IO) {
                AccountStore.importOrReplace(context, displayHint, bytes)
            }
            if (newId == null)
            {
                status = "That file doesn't look like a Jagex credentials.properties (no JX_* keys)."
                return@launch
            }
            refreshList()
            busyId = newId
            status = "Imported. Detecting account type…"
            withContext(Dispatchers.IO) { AccountStore.refreshFromHiscores(context, newId) }
            refreshList()
            busyId = null
            status = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarker),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "RuneLite",
                color = BrandOrange,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Pick the account to launch with",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))

            if (imported.isEmpty())
            {
                EmptyState()
                Spacer(Modifier.height(16.dp))
            }
            else
            {
                SectionLabel("Imported credentials")
                Spacer(Modifier.height(8.dp))
                for (acc in imported)
                {
                    ImportedAccountCard(
                        account = acc,
                        busy = acc.id == busyId,
                        onLaunch = {
                            AccountStore.setActive(context, acc.id)
                            onSelect(AccountSelection(credentialsFile = acc.file))
                        },
                        onRefresh = {
                            busyId = acc.id
                            status = "Refreshing ${acc.displayName} from hiscores…"
                            scope.launch {
                                withContext(Dispatchers.IO) { AccountStore.refreshFromHiscores(context, acc.id) }
                                refreshList()
                                busyId = null
                                status = null
                            }
                        },
                        onDelete = {
                            AccountStore.delete(context, acc.id)
                            refreshList()
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            PrimaryButton("Import credentials.properties") {
                importLauncher.launch(arrayOf("text/plain", "*/*"))
            }
            if (status != null)
            {
                Spacer(Modifier.height(12.dp))
                Text(status!!, color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tokens are stored in this app's private storage and are only ever sent to Jagex.",
                color = TextDisabled,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun EmptyState()
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No accounts yet. On your PC, launch RuneLite through the Jagex Launcher with " +
                "--insecure-write-credentials, then import the credentials.properties it writes " +
                "to your .runelite folder.",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String)
{
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = TextDisabled, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(SurfaceBorder))
    }
}


@Composable
private fun isWatch(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { net.runelite.mp.update.UpdateGate.isWatch(context) }
}

@Composable
private fun ImportedAccountCard(
    account: AccountStore.Account,
    busy: Boolean,
    onLaunch: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp)),
    ) {
        val watch = isWatch()
        Column(modifier = Modifier.padding(14.dp)) {
            // On a watch the Launch button would squeeze out the name; tapping the name launches instead.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (watch) Modifier.clickable(enabled = !busy, onClick = onLaunch) else Modifier,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountTypePill(account.accountType)
                        if (account.totalLevel > 0)
                        {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Total ${"%,d".format(account.totalLevel)}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        if (account.rank > 0)
                        {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Rank #${"%,d".format(account.rank)}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                if (!watch) Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandOrange)
                        .clickable(enabled = !busy, onClick = onLaunch)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (busy) "…" else "Launch",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val refreshed = when
            {
                account.lastRefreshedEpochMs <= 0 -> "Hiscores not yet checked"
                else -> "Refreshed ${relativeTime(account.lastRefreshedEpochMs)}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(refreshed, color = TextDisabled, fontSize = 10.sp, modifier = Modifier.weight(1f))
                MiniBtn("Refresh", onRefresh)
                Spacer(Modifier.size(6.dp))
                MiniBtn("Remove", onDelete)
            }
        }
    }
}

@Composable
private fun AccountTypePill(type: AccountStore.AccountType)
{
    val (bg, fg) = when (type)
    {
        AccountStore.AccountType.NORMAL -> Color(0xFF2F4F2F) to Color(0xFF8FE188)
        AccountStore.AccountType.IRONMAN -> Color(0xFF42424A) to Color(0xFFD0D0D0)
        AccountStore.AccountType.HARDCORE_IRONMAN -> Color(0xFF4A1F1F) to Color(0xFFE57373)
        AccountStore.AccountType.ULTIMATE_IRONMAN -> Color(0xFF3A2C44) to Color(0xFFC79DD9)
        AccountStore.AccountType.UNKNOWN -> Color(0xFF2A2A2A) to TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(type.label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBtn(label: String, onClick: () -> Unit)
{
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MediumGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandOrange)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

private fun displayNameFromUri(context: Context, uri: Uri): String?
{
    return try
    {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }
    catch (t: Throwable) { null }
}

/** "5m ago", "3h ago", "yesterday", "Apr 14" — a tiny relative-time formatter so cards
 *  don't show the same epoch number that was generated a tick after import. */
private fun relativeTime(epochMs: Long): String
{
    val delta = (System.currentTimeMillis() - epochMs).coerceAtLeast(0)
    val seconds = delta / 1000
    return when
    {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        seconds < 604800 -> "${seconds / 86400}d ago"
        else ->
        {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
            java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(cal.time)
        }
    }
}
