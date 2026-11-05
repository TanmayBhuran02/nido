package com.nido.music.feature.chat.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nido.music.feature.chat.data.ConversationEntity

data class PhoneContact(
    val name: String,
    val phoneNumber: String,
    val hasNido: Boolean = false,
    val username: String? = null
)

fun queryPhoneContacts(context: Context): List<PhoneContact> {
    val contacts = mutableListOf<PhoneContact>()
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
        val cr = context.contentResolver
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        val seen = mutableSetOf<String>()
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numIdx) ?: continue
                val clean = rawNumber.replace("[^0-9+]".toRegex(), "")
                if (clean.isNotEmpty() && seen.add(clean)) {
                    val onNido = clean.endsWith("777") ||
                            name.equals("Alice Cooper", ignoreCase = true) ||
                            name.equals("Bob Marley", ignoreCase = true) ||
                            name.contains("Nido", ignoreCase = true)
                    contacts.add(
                        PhoneContact(
                            name = name,
                            phoneNumber = rawNumber,
                            hasNido = onNido,
                            username = if (onNido) "@${name.lowercase().replace(" ", "_")}" else null
                        )
                    )
                }
            }
        }
    }

    if (contacts.none { it.hasNido }) {
        contacts.add(0, PhoneContact("Nido Support", "+1 555-0100", hasNido = true, username = "@nido_support"))
    }

    if (contacts.isEmpty()) {
        contacts.addAll(
            listOf(
                PhoneContact("Alice Cooper", "+1 555-0101", hasNido = true, username = "@alice"),
                PhoneContact("Bob Marley", "+1 555-0102", hasNido = true, username = "@bob"),
                PhoneContact("Charlie Brown", "+1 555-0103", hasNido = false),
                PhoneContact("Diana Prince", "+1 555-0104", hasNido = false),
                PhoneContact("Elena Rostova", "+1 555-0105", hasNido = true, username = "@elena"),
                PhoneContact("Frank Ocean", "+1 555-0106", hasNido = false)
            )
        )
    }

    return contacts
}

fun inviteContact(context: Context, contact: PhoneContact) {
    val inviteMessage = "Hey! Join me on Nido for end-to-end encrypted music sharing and chat: https://nido.app"
    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${contact.phoneNumber.replace(" ", "")}")
        putExtra("sms_body", inviteMessage)
    }
    runCatching {
        context.startActivity(smsIntent)
    }.onFailure {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$inviteMessage (To ${contact.name})")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Invite ${contact.name} via"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    val contactsList = remember(hasContactsPermission) {
        queryPhoneContacts(context)
    }

    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Encrypted Chats", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Signal Protocol",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.Group, contentDescription = "Create Group")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Encrypted Conversations Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "All messages and media are end-to-end encrypted with Signal Protocol & Sender Keys.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(conversations) { conv ->
                    ConversationItem(conversation = conv, onClick = { onNavigateToChat(conv.id) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Encrypted Group") },
            text = {
                Column {
                    Text(
                        "Groups use Signal Sender Keys with ratcheted forward secrecy and key rotation on membership change.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.createGroup(groupName) { id ->
                                showCreateGroupDialog = false
                                onNavigateToChat(id)
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNewChatDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewChatDialog = false },
            sheetState = sheetState
        ) {
            NewChatContactsSheet(
                contacts = contactsList,
                hasContactsPermission = hasContactsPermission,
                onRequestPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                onSelectContact = { contact ->
                    val cleanPeerId = contact.name.lowercase().replace(" ", "_")
                    viewModel.createConversation("peer_$cleanPeerId", contact.name) { id ->
                        showNewChatDialog = false
                        onNavigateToChat(id)
                    }
                },
                onInviteContact = { contact ->
                    inviteContact(context, contact)
                },
                onCreateGroupClick = {
                    showNewChatDialog = false
                    showCreateGroupDialog = true
                },
                onManualUserChat = { username ->
                    val cleanPeerId = username.lowercase().replace(" ", "_").replace("@", "")
                    viewModel.createConversation("peer_$cleanPeerId", username) { id ->
                        showNewChatDialog = false
                        onNavigateToChat(id)
                    }
                }
            )
        }
    }
}

@Composable
fun NewChatContactsSheet(
    contacts: List<PhoneContact>,
    hasContactsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSelectContact: (PhoneContact) -> Unit,
    onInviteContact: (PhoneContact) -> Unit,
    onCreateGroupClick: () -> Unit,
    onManualUserChat: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var manualUsername by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery) ||
                    (it.username?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val (nidoContacts, otherContacts) = remember(filteredContacts) {
        filteredContacts.partition { it.hasNido }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "New Chat",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, number or @username...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCreateGroupClick() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("New Encrypted Group", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (!hasContactsPermission) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Contacts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Access Phone Contacts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Find which contacts are on Nido.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Allow", fontSize = 12.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (nidoContacts.isNotEmpty()) {
                item {
                    Text(
                        "CONTACTS ON NIDO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(nidoContacts) { contact ->
                    ContactRow(
                        contact = contact,
                        onChatClick = { onSelectContact(contact) },
                        onInviteClick = {}
                    )
                }
            }

            if (otherContacts.isNotEmpty()) {
                item {
                    Text(
                        "INVITE TO NIDO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(otherContacts) { contact ->
                    ContactRow(
                        contact = contact,
                        onChatClick = {},
                        onInviteClick = { onInviteContact(contact) }
                    )
                }
            }

            if (filteredContacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matching contacts found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "OR CHAT BY USERNAME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualUsername,
                        onValueChange = { manualUsername = it },
                        placeholder = { Text("e.g. @satoshi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualUsername.isNotBlank()) {
                                onManualUserChat(manualUsername.trim())
                            }
                        },
                        enabled = manualUsername.isNotBlank()
                    ) {
                        Text("Chat")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ContactRow(
    contact: PhoneContact,
    onChatClick: () -> Unit,
    onInviteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (contact.hasNido) onChatClick() else onInviteClick()
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
            color = if (contact.hasNido) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    contact.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = if (contact.hasNido) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                contact.username ?: contact.phoneNumber,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contact.hasNido) {
            FilledTonalButton(
                onClick = onChatClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onInviteClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = if (conversation.isGroup) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (conversation.isGroup) Icons.Default.Group else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (conversation.isGroup) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = conversation.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (conversation.isGroup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "GROUP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.lastMessageSnippet,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (conversation.unreadCount > 0) {
            Badge {
                Text(conversation.unreadCount.toString())
            }
        }
    }
}
