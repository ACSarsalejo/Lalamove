<?php
// Database Configuration — matches your existing XAMPP MySQL setup
$DB_HOST = 'localhost';
$DB_NAME = 'lalamove';  // Change this if your database has a different name
$DB_USER = 'root';
$DB_PASS = '';           // Default XAMPP has no password

// Create connection
$conn = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME);

if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database connection failed: ' . $conn->connect_error]);
    exit;
}

$conn->set_charset('utf8mb4');

// Helper: send JSON response
function sendResponse($success, $message, $data = null) {
    header('Content-Type: application/json');
    $response = ['success' => $success, 'message' => $message];
    if ($data !== null) {
        $response['data'] = $data;
    }
    echo json_encode($response);
    exit;
}
?>
