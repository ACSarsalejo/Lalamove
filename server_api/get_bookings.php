<?php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendResponse(false, 'Only GET method allowed');
}

$custId = intval($_GET['cust_id'] ?? 0);
if ($custId <= 0) {
    sendResponse(false, 'Valid customer ID is required');
}

$stmt = $conn->prepare("
    SELECT b.Book_ID, b.Book_VhclTypeID, b.Book_TotalFare, b.Book_Status, 
           b.Book_ScheduledTime, b.Book_CreatedAt, b.Book_Pickuploc, b.Book_Dropoffloc,
           b.Book_Notes, b.Book_AddServices, b.Book_IsRated, b.Book_RatingGiven,
           d.Drvr_firstName, d.Drvr_LastName, d.Drvr_PhoneNum, d.Drvr_Rating
    FROM booking b
    LEFT JOIN driver d ON b.Book_DrvrID = d.Drvr_ID
    WHERE b.Book_CustID = ?
    ORDER BY b.Book_CreatedAt DESC
");
$stmt->bind_param('i', $custId);
$stmt->execute();
$result = $stmt->get_result();

$bookings = [];
while ($row = $result->fetch_assoc()) {
    $bookings[] = [
        'book_id'          => $row['Book_ID'],
        'vehicle_type'     => $row['Book_VhclTypeID'],
        'total_fare'       => floatval($row['Book_TotalFare']),
        'status'           => $row['Book_Status'],
        'scheduled_time'   => $row['Book_ScheduledTime'],
        'created_at'       => $row['Book_CreatedAt'],
        'pickup_location'  => $row['Book_Pickuploc'],
        'dropoff_location' => $row['Book_Dropoffloc'],
        'notes'            => $row['Book_Notes'],
        'add_services'     => $row['Book_AddServices'],
        'is_rated'         => intval($row['Book_IsRated']),
        'rating_given'     => $row['Book_RatingGiven'] ? intval($row['Book_RatingGiven']) : null,
        'driver_name'      => $row['Drvr_firstName'] ? $row['Drvr_firstName'] . ' ' . $row['Drvr_LastName'] : null,
        'driver_phone'     => $row['Drvr_PhoneNum'],
        'driver_rating'    => $row['Drvr_Rating'] ? floatval($row['Drvr_Rating']) : null
    ];
}

sendResponse(true, 'Bookings retrieved', $bookings);
?>
