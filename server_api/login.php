<?php
require_once 'config.php';

// Only accept POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Only POST method allowed');
}

// Get input
$input = json_decode(file_get_contents('php://input'), true);
$email = trim($input['email'] ?? '');
$password = $input['password'] ?? '';

if (empty($email) || empty($password)) {
    sendResponse(false, 'Email and password are required');
}

// Look up account
$stmt = $conn->prepare("SELECT Acct_Id, Acct_Email, Acct_Phone, Acct_Password, Acct_Role, Acct_Status FROM accounts WHERE Acct_Email = ?");
$stmt->bind_param('s', $email);
$stmt->execute();
$result = $stmt->get_result();

if ($result->num_rows === 0) {
    sendResponse(false, 'Invalid email or password');
}

$account = $result->fetch_assoc();

// Check password (bcrypt)
if (!password_verify($password, $account['Acct_Password'])) {
    sendResponse(false, 'Invalid email or password');
}

// Check if account is active
if ($account['Acct_Status'] !== 'active') {
    sendResponse(false, 'Account is ' . $account['Acct_Status']);
}

// Update last login
$updateStmt = $conn->prepare("UPDATE accounts SET Acct_LastLogin = NOW() WHERE Acct_Id = ?");
$updateStmt->bind_param('i', $account['Acct_Id']);
$updateStmt->execute();

// Build response based on role
$userData = [
    'acct_id'   => $account['Acct_Id'],
    'email'     => $account['Acct_Email'],
    'phone'     => $account['Acct_Phone'],
    'role'      => $account['Acct_Role']
];

if ($account['Acct_Role'] === 'customer') {
    $custStmt = $conn->prepare("SELECT Cust_ID, Cust_firstName, Cust_lastName FROM customer WHERE Cust_AcctId = ?");
    $custStmt->bind_param('i', $account['Acct_Id']);
    $custStmt->execute();
    $custResult = $custStmt->get_result();
    if ($custRow = $custResult->fetch_assoc()) {
        $userData['cust_id']    = $custRow['Cust_ID'];
        $userData['first_name'] = $custRow['Cust_firstName'];
        $userData['last_name']  = $custRow['Cust_lastName'];
    }
} elseif ($account['Acct_Role'] === 'driver') {
    $drvStmt = $conn->prepare("SELECT Drvr_ID, Drvr_firstName, Drvr_LastName, Drvr_Rating, Drvr_Status, Drvr_IsVerified FROM driver WHERE Drvr_AcctId = ?");
    $drvStmt->bind_param('i', $account['Acct_Id']);
    $drvStmt->execute();
    $drvResult = $drvStmt->get_result();
    if ($drvRow = $drvResult->fetch_assoc()) {
        $userData['drvr_id']     = $drvRow['Drvr_ID'];
        $userData['first_name']  = $drvRow['Drvr_firstName'];
        $userData['last_name']   = $drvRow['Drvr_LastName'];
        $userData['rating']      = $drvRow['Drvr_Rating'];
        $userData['status']      = $drvRow['Drvr_Status'];
        $userData['is_verified'] = $drvRow['Drvr_IsVerified'];
    }
}

sendResponse(true, 'Login successful', $userData);
?>
