<?php
require_once 'config.php';

$action = $_GET['action'] ?? '';

switch ($action) {
    case 'update_tracking':
        updateTracking();
        break;
    case 'complete_delivery':
        completeDelivery();
        break;
    case 'cashout':
        requestCashout();
        break;
    default:
        sendResponse(false, 'Invalid action');
}

function updateTracking() {
    global $conn;
    $input = json_decode(file_get_contents('php://input'), true);
    $bookId = intval($input['book_id'] ?? 0);
    $lat = floatval($input['latitude'] ?? 0);
    $lng = floatval($input['longitude'] ?? 0);
    $status = trim($input['status'] ?? 'driver_en_route');

    if ($bookId <= 0) sendResponse(false, 'Invalid booking ID');

    $stmt = $conn->prepare("INSERT INTO movementlog (Move_BookID, Move_Status, Move_Latitude, Move_Longitude) VALUES (?, ?, ?, ?)");
    $stmt->bind_param('isdd', $bookId, $status, $lat, $lng);
    
    if ($stmt->execute()) {
        sendResponse(true, 'Location updated');
    } else {
        sendResponse(false, 'Failed to update location');
    }
}

function completeDelivery() {
    global $conn;
    $input = json_decode(file_get_contents('php://input'), true);
    $dlvrId = intval($input['delivery_id'] ?? 0);
    $proofUrl = trim($input['proof_url'] ?? '');

    if ($dlvrId <= 0) sendResponse(false, 'Invalid delivery ID');

    $stmt = $conn->prepare("UPDATE delivery SET Dlvr_Status = 'completed', Dlvr_ProofOfDelivery = ? WHERE Dlvr_ID = ?");
    $stmt->bind_param('si', $proofUrl, $dlvrId);
    
    if ($stmt->execute()) {
        sendResponse(true, 'Delivery completed');
    } else {
        sendResponse(false, 'Failed to complete delivery');
    }
}

function requestCashout() {
    global $conn;
    $input = json_decode(file_get_contents('php://input'), true);
    $drvrId = intval($input['driver_id'] ?? 0);
    $amount = floatval($input['amount'] ?? 0);

    if ($drvrId <= 0 || $amount <= 0) sendResponse(false, 'Invalid request');

    // Check balance first
    $checkStmt = $conn->prepare("SELECT Drvr_WalletBalance FROM driver WHERE Drvr_ID = ?");
    $checkStmt->bind_param('i', $drvrId);
    $checkStmt->execute();
    $result = $checkStmt->get_result()->fetch_assoc();

    if ($result['Drvr_WalletBalance'] < $amount) {
        sendResponse(false, 'Insufficient balance');
        return;
    }

    $conn->begin_transaction();
    try {
        // 1. Deduct from driver wallet
        $updateStmt = $conn->prepare("UPDATE driver SET Drvr_WalletBalance = Drvr_WalletBalance - ? WHERE Drvr_ID = ?");
        $updateStmt->bind_param('di', $amount, $drvrId);
        $updateStmt->execute();

        // 2. Log transaction
        $refNum = 'CASHOUT-' . time();
        $tranStmt = $conn->prepare("INSERT INTO transaction (Tran_TargetType, Tran_TargetID, Tran_Type, Tran_Amount, Tran_ReferenceNum) VALUES ('driver', ?, 'deduction', ?, ?)");
        $tranStmt->bind_param('ids', $drvrId, $amount, $refNum);
        $tranStmt->execute();

        $conn->commit();
        sendResponse(true, 'Cashout request processed', ['reference' => $refNum]);
    } catch (Exception $e) {
        $conn->rollback();
        sendResponse(false, 'Cashout failed: ' . $e->getMessage());
    }
}
?>
