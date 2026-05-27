<?php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Only POST method allowed');
}

$input = json_decode(file_get_contents('php://input'), true);

$custId      = intval($input['cust_id'] ?? 0);
$vehicleType = trim($input['vehicle_type'] ?? '');
$totalFare   = floatval($input['total_fare'] ?? 0);
$pickupLoc   = trim($input['pickup_location'] ?? '');
$notes       = trim($input['notes'] ?? '');
$addServices = trim($input['add_services'] ?? '');
$stops       = $input['stops'] ?? []; // Array of {address, items: [{name, category, qty, size, weight}]}

// Validation
if ($custId <= 0 || empty($vehicleType) || $totalFare <= 0 || empty($pickupLoc) || empty($stops)) {
    sendResponse(false, 'Missing required booking fields or stops');
}

$conn->begin_transaction();

try {
    // 1. Insert booking
    // Using the last stop's address as Book_Dropoffloc for the main booking table compatibility
    $mainDropoff = $stops[count($stops) - 1]['address'] ?? '';
    
    $bookStmt = $conn->prepare("INSERT INTO booking (Book_CustID, Book_VhclTypeID, Book_TotalFare, Book_Status, Book_ScheduledTime, Book_Pickuploc, Book_Dropoffloc, Book_Notes, Book_AddServices) VALUES (?, ?, ?, 'pending', NOW(), ?, ?, ?, ?)");
    $bookStmt->bind_param('isdssss', $custId, $vehicleType, $totalFare, $pickupLoc, $mainDropoff, $notes, $addServices);
    $bookStmt->execute();
    $bookId = $conn->insert_id;

    // 2. Loop through stops
    $stopNum = 1;
    foreach ($stops as $stop) {
        $address = trim($stop['address'] ?? '');
        $items   = $stop['items'] ?? [];

        if (empty($address)) continue;

        $dlvrStmt = $conn->prepare("INSERT INTO delivery (Dlvr_BookID, Dlvr_StopNumber, Dlvr_Address) VALUES (?, ?, ?)");
        $dlvrStmt->bind_param('iis', $bookId, $stopNum, $address);
        $dlvrStmt->execute();
        $dlvrId = $conn->insert_id;

        // 3. Loop through items for this stop
        foreach ($items as $item) {
            $itemName = trim($item['name'] ?? 'Package');
            $itemCategory = trim($item['category'] ?? '');
            $itemQty = intval($item['qty'] ?? 1);
            $itemSize = trim($item['size'] ?? '');
            $itemWeight = floatval($item['weight'] ?? 0);

            $itemStmt = $conn->prepare("INSERT INTO item (Item_DlvrID, Item_Name, Item_Category, Item_Quantity, Item_Size, Item_WeightKG) VALUES (?, ?, ?, ?, ?, ?)");
            $itemStmt->bind_param('issisd', $dlvrId, $itemName, $itemCategory, $itemQty, $itemSize, $itemWeight);
            $itemStmt->execute();
        }
        $stopNum++;
    }

    $conn->commit();

    sendResponse(true, 'Multi-stop booking created successfully', [
        'book_id' => $bookId,
        'status'  => 'pending',
        'fare'    => $totalFare,
        'stops_count' => $stopNum - 1
    ]);
} catch (Exception $e) {
    $conn->rollback();
    sendResponse(false, 'Booking failed: ' . $e->getMessage());
}
?>
