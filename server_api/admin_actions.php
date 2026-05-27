<?php
require_once 'config.php';

$action = $_GET['action'] ?? '';

switch ($action) {
    case 'get_pending':
        getPending();
        break;
    case 'verify_driver':
        verifyDriver();
        break;
    case 'verify_vehicle':
        verifyVehicle();
        break;
    default:
        sendResponse(false, 'Invalid action');
}

function getPending() {
    global $conn;
    
    $drivers = $conn->query("SELECT * FROM driver WHERE Drvr_IsVerified = 0")->fetch_all(MYSQLI_ASSOC);
    $vehicles = $conn->query("SELECT * FROM vehicle WHERE Vhcl_Status = 'pending'")->fetch_all(MYSQLI_ASSOC);
    
    sendResponse(true, 'Pending approvals retrieved', [
        'drivers' => $drivers,
        'vehicles' => $vehicles
    ]);
}

function verifyDriver() {
    global $conn;
    $input = json_decode(file_get_contents('php://input'), true);
    $drvrId = intval($input['driver_id'] ?? 0);
    $status = intval($input['is_verified'] ?? 1); // 1 for verify, 0 for revoke?

    if ($drvrId <= 0) sendResponse(false, 'Invalid driver ID');

    $stmt = $conn->prepare("UPDATE driver SET Drvr_IsVerified = ? WHERE Drvr_ID = ?");
    $stmt->bind_param('ii', $status, $drvrId);
    
    if ($stmt->execute()) {
        sendResponse(true, 'Driver verification status updated');
    } else {
        sendResponse(false, 'Failed to update driver status');
    }
}

function verifyVehicle() {
    global $conn;
    $input = json_decode(file_get_contents('php://input'), true);
    $vhclId = intval($input['vehicle_id'] ?? 0);
    $status = trim($input['status'] ?? 'verified'); // verified, rejected, inactive

    if ($vhclId <= 0) sendResponse(false, 'Invalid vehicle ID');

    $stmt = $conn->prepare("UPDATE vehicle SET Vhcl_Status = ? WHERE Vhcl_ID = ?");
    $stmt->bind_param('si', $status, $vhclId);
    
    if ($stmt->execute()) {
        sendResponse(true, 'Vehicle status updated');
    } else {
        sendResponse(false, 'Failed to update vehicle status');
    }
}
?>
