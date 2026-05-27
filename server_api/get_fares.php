<?php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendResponse(false, 'Only GET method allowed');
}

$stmt = $conn->prepare("SELECT Fare_VhclType, Fare_Label, Fare_Emoji, Fare_BaseFare, Fare_PerKm, Fare_IsActive FROM fareconfig WHERE Fare_IsActive = 1 ORDER BY Fare_BaseFare ASC");
$stmt->execute();
$result = $stmt->get_result();

$fares = [];
while ($row = $result->fetch_assoc()) {
    $fares[] = [
        'vehicle_type' => $row['Fare_VhclType'],
        'label'        => $row['Fare_Label'],
        'emoji'        => $row['Fare_Emoji'],
        'base_fare'    => floatval($row['Fare_BaseFare']),
        'per_km'       => floatval($row['Fare_PerKm'])
    ];
}

sendResponse(true, 'Fares retrieved', $fares);
?>
