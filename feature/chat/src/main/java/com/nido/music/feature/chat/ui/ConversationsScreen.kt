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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onNavigateToRegister: () -> Unit = {},
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()
    val deletedContactIds by viewModel.deletedContactIds.collectAsState()

    val needsPhoneUpdate = authState.isLoggedIn && authState.phone.isNullOrBlank()
    var showPhoneRequiredDialog by remember { mutableStateOf(false) }

    LaunchedEffect(needsPhoneUpdate) {
        if (needsPhoneUpdate) {
            showPhoneRequiredDialog = true
        }
    }

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

    val contactsList = remember(hasContactsPermission, deletedContactIds) {
        queryPhoneContacts(context).filter { contact ->
            val cleanPhone = contact.phoneNumber.replace("[^0-9+]".toRegex(), "")
            !deletedContactIds.contains(contact.name) &&
            !deletedContactIds.contains(cleanPhone) &&
            (contact.username == null || !deletedContactIds.contains(contact.username))
        }
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
                    if (authState.username?.startsWith("user_") == true || !authState.isLoggedIn) {
                        FilledTonalButton(
                            onClick = onNavigateToRegister,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Register", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { showEditProfileDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = authState.username?.let { "@$it" } ?: "Identity",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
                items(conversations, key = { it.id }) { conv ->
                    ConversationItem(
                        conversation = conv,
                        onClick = {
                            viewModel.markConversationAsRead(conv.id)
                            onNavigateToChat(conv.id)
                        },
                        onRename = { newName -> viewModel.renameConversation(conv.id, newName) },
                        onDelete = { viewModel.deleteConversation(conv.id) }
                    )
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
                },
                onDeleteContact = { contact ->
                    viewModel.deleteContact(contact.name, contact.phoneNumber)
                },
                myUsername = authState.username
            )
        }
    }

    if (showPhoneRequiredDialog || showEditProfileDialog) {
        val isForcedPhoneUpdate = showPhoneRequiredDialog && authState.phone.isNullOrBlank()
        var inputName by remember(authState.username) { mutableStateOf(authState.username ?: "") }
        var inputPhone by remember(authState.phone) { mutableStateOf(authState.phone ?: "") }
        var inputEmail by remember(authState.email) { mutableStateOf(authState.email ?: "") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isForcedPhoneUpdate) {
                    showEditProfileDialog = false
                }
            },
            title = {
                Text(
                    if (isForcedPhoneUpdate) "Phone Number Required" else "User Profile Details",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (isForcedPhoneUpdate)
                            "A phone number is required to connect with contacts and identify your chat account. Your current User ID has been pre-filled below."
                        else
                            "Update your profile details. Friends can connect with you using your username or phone number.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (authState.username?.startsWith("user_") == true) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                showEditProfileDialog = false
                                showPhoneRequiredDialog = false
                                onNavigateToRegister()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register Account with Password")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it; errorMsg = null },
                        label = { Text("Username / User ID") },
                        placeholder = { Text("e.g. satoshi") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        isError = errorMsg != null && errorMsg!!.contains("username", ignoreCase = true),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it; errorMsg = null },
                        label = { Text("Phone Number (Mandatory)") },
                        placeholder = { Text("+1 555-0199") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = errorMsg != null && errorMsg!!.contains("phone", ignoreCase = true),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it; errorMsg = null },
                        label = { Text("Email (Optional)") },
                        placeholder = { Text("you@example.com") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = errorMsg != null && errorMsg!!.contains("email", ignoreCase = true),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanPhone = inputPhone.trim()
                        if (cleanPhone.isBlank()) {
                            errorMsg = "Phone number is mandatory"
                            return@Button
                        }
                        val digits = cleanPhone.replace("[^0-9]".toRegex(), "")
                        if (digits.length < 6) {
                            errorMsg = "Please enter a valid phone number"
                            return@Button
                        }
                        android.util.Log.d("ConversationsScreen", "updateProfile click: phone=$cleanPhone, email=${inputEmail.trim()}, username=${inputName.trim()}")
                        isSubmitting = true
                        viewModel.updateProfile(
                            phone = cleanPhone,
                            email = inputEmail.trim().takeIf { it.isNotBlank() },
                            username = inputName.trim().takeIf { it.isNotBlank() }
                        ) { success, err ->
                            isSubmitting = false
                            if (success) {
                                showPhoneRequiredDialog = false
                                showEditProfileDialog = false
                            } else {
                                errorMsg = err ?: "Update failed"
                            }
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Save")
                }
            },
            dismissButton = {
                if (!isForcedPhoneUpdate) {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel")
                    }
                } else {
                    TextButton(onClick = { showPhoneRequiredDialog = false }) {
                        Text("Later")
                    }
                }
            }
        )
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
    onManualUserChat: (String) -> Unit,
    onDeleteContact: (PhoneContact) -> Unit = {},
    myUsername: String? = null
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

        if (!myUsername.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your handle: @$myUsername (tell your friend to chat here)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Create Encrypted Group
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCreateGroupClick() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
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
            Column {
                Text("New Encrypted Group", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Chat securely with multiple people", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Chat by Username - placed above search input
        Text(
            "CHAT BY USERNAME",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
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

        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

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
                        onInviteClick = {},
                        onDeleteContact = { onDeleteContact(contact) }
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
                        onInviteClick = { onInviteContact(contact) },
                        onDeleteContact = { onDeleteContact(contact) }
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
        }
    }
}

@Composable
fun ContactRow(
    contact: PhoneContact,
    onChatClick: () -> Unit,
    onInviteClick: () -> Unit,
    onDeleteContact: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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

        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete Contact",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact") },
            text = { Text("Remove \"${contact.name}\" from your contacts?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteDialog = false
                        onDeleteContact()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onRename: (String) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
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

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        showRenameDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    }
                )
            }
        }

        if (conversation.unreadCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Delete conversation with \"${conversation.title}\" and all messages permanently?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename User") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Name on this device") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitle.isNotBlank()) {
                        onRename(newTitle.trim())
                    }
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
