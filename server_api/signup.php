<?php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Only POST method allowed');
}

$input = json_decode(file_get_contents('php://input'), true);
$firstName = trim($input['first_name'] ?? '');
$lastName  = trim($input['last_name'] ?? '');
$email     = trim($input['email'] ?? '');
$phone     = trim($input['phone'] ?? '');
$password  = $input['password'] ?? '';

// Validation
if (empty($firstName) || empty($lastName) || empty($email) || empty($phone) || empty($password)) {
    sendResponse(false, 'All fields are required');
}

// Check if email already exists
$checkStmt = $conn->prepare("SELECT Acct_Id FROM accounts WHERE Acct_Email = ?");
$checkStmt->bind_param('s', $email);
$checkStmt->execute();
if ($checkStmt->get_result()->num_rows > 0) {
    sendResponse(false, 'Email already registered');
}

// Check if phone already exists
$phoneStmt = $conn->prepare("SELECT Acct_Id FROM accounts WHERE Acct_Phone = ?");
$phoneStmt->bind_param('s', $phone);
$phoneStmt->execute();
if ($phoneStmt->get_result()->num_rows > 0) {
    sendResponse(false, 'Phone number already registered');
}

// Hash password (same bcrypt as website)
$hashedPassword = password_hash($password, PASSWORD_BCRYPT);

// Begin transaction
$conn->begin_transaction();

try {
    // Insert into accounts
    $acctStmt = $conn->prepare("INSERT INTO accounts (Acct_Email, Acct_Phone, Acct_Password, Acct_Role) VALUES (?, ?, ?, 'customer')");
    $acctStmt->bind_param('sss', $email, $phone, $hashedPassword);
    $acctStmt->execute();
    $acctId = $conn->insert_id;

    // Insert into customer
    $custStmt = $conn->prepare("INSERT INTO customer (Cust_AcctId, Cust_firstName, Cust_lastName, Cust_Phone, Cust_Email) VALUES (?, ?, ?, ?, ?)");
    $custStmt->bind_param('issss', $acctId, $firstName, $lastName, $phone, $email);
    $custStmt->execute();
    $custId = $conn->insert_id;

    $conn->commit();

    sendResponse(true, 'Account created successfully', [
        'acct_id'    => $acctId,
        'cust_id'    => $custId,
        'email'      => $email,
        'first_name' => $firstName,
        'last_name'  => $lastName,
        'role'       => 'customer'
    ]);
} catch (Exception $e) {
    $conn->rollback();
    sendResponse(false, 'Registration failed: ' . $e->getMessage());
}
?>
