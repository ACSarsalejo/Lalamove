package com.example.lalamove

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object DatabaseSeeder {

    fun seedDatabase() {
        val db = FirebaseFirestore.getInstance()

        // --- 0. CLEANUP (Remove legacy collections that don't match SQL schema) ---
        // These were used in previous iterations before aligning with lalamove_database.txt
        val legacyCollections = listOf("coupons", "settings")
        for (coll in legacyCollections) {
            db.collection(coll).get().addOnSuccessListener { docs ->
                for (doc in docs) doc.reference.delete()
            }
        }

        // --- 1. ACCOUNTS ---
        val accounts = listOf(
            hashMapOf("Acct_Id" to 2, "Acct_Email" to "shin@gmail.com", "Acct_Phone" to "+6309935898770", "Acct_Password" to "\$2y\$10\$K2zkpkJZiJrzz8hgtA21WuBnWKR/RGdlGNumGr/cnOU.YxjsRisIG", "Acct_Role" to "customer", "Acct_MustChangePassword" to false, "Acct_Status" to "active", "Acct_LastLogin" to "2026-05-07 18:23:09", "Acct_CreatedAt" to "2026-04-21 02:06:24", "Acct_UpdatedAt" to "2026-05-07 18:23:09"),
            hashMapOf("Acct_Id" to 3, "Acct_Email" to "shiko@gmail.com", "Acct_Phone" to "+6309935898779", "Acct_Password" to "\$2y\$10\$5mqtRty0kTbX3opZGKIEJ.tyf0GgaVqHQei/yA9NMlGG6Fu06zw1e", "Acct_Role" to "customer", "Acct_MustChangePassword" to false, "Acct_Status" to "active", "Acct_LastLogin" to "2026-04-21 02:13:06", "Acct_CreatedAt" to "2026-04-21 02:07:40", "Acct_UpdatedAt" to "2026-04-21 02:13:06"),
            hashMapOf("Acct_Id" to 5, "Acct_Email" to "jamesestacion@gmail.com", "Acct_Phone" to "+6309192198198", "Acct_Password" to "\$2y\$10\$kqZg7qALt3brEWzr4TNMvO5p6GThQbWaDGRTl1mp92g2nX9pzYjHu", "Acct_Role" to "driver", "Acct_MustChangePassword" to false, "Acct_Status" to "active", "Acct_LastLogin" to "2026-05-07 20:02:01", "Acct_CreatedAt" to "2026-05-07 14:33:11", "Acct_UpdatedAt" to "2026-05-07 20:02:01")
        )
        for (a in accounts) db.collection("accounts").document(a["Acct_Id"].toString()).set(a)

        // --- 2. ADMIN ---
        val admins = listOf(
            hashMapOf("Admin_ID" to 3000150626L, "Admin_Username" to "Admin123", "Admin_Password" to "123Admin", "Admin_Name" to "Achilly Ceasar B. Sarsalejo", "Admin_CreatedAt" to "2026-05-06 00:53:35", "Admin_LastLogin" to "2026-05-07 20:24:05")
        )
        for (ad in admins) db.collection("admin").document(ad["Admin_ID"].toString()).set(ad)

        // --- 3. CUSTOMER ---
        val customers = listOf(
            hashMapOf("Cust_ID" to 1000142126L, "Cust_AcctId" to 2, "Cust_FirstName" to "Achilly", "Cust_LastName" to "Godzilla", "Cust_Phone" to "+6309935898770", "Cust_Email" to "shin@gmail.com", "Cust_IsBanned" to false),
            hashMapOf("Cust_ID" to 1000242126L, "Cust_AcctId" to 3, "Cust_FirstName" to "shiko", "Cust_LastName" to "tantan", "Cust_Phone" to "+6309935898779", "Cust_Email" to "shiko@gmail.com", "Cust_IsBanned" to false)
        )
        for (c in customers) db.collection("customer").document(c["Cust_ID"].toString()).set(c)

        // --- 4. DRIVER ---
        val drivers = listOf(
            hashMapOf("Drvr_ID" to 2000150726L, "Drvr_AcctId" to 5, "Drvr_FirstName" to "James", "Drvr_LastName" to "Estacion", "Drvr_VhclTypeID" to "motorcycle", "Drvr_PhoneNum" to "+6309192198198", "Drvr_LicenseNum" to "G35-26-000528", "Drvr_LicensePhoto" to "uploads/drivers/license_5_1778135591.png", "Drvr_ProfilePhoto" to "uploads/drivers/profile_5.jpg", "Drvr_Rating" to 3.50, "Drvr_RatingCount" to 4, "Drvr_WalletBalance" to 524.00, "Drvr_CommissionRate" to 0.80, "Drvr_Status" to "online", "Drvr_IsVerified" to true, "Drvr_VehicleID" to 2, "Drvr_AreaID" to 1)
        )
        for (d in drivers) db.collection("driver").document(d["Drvr_ID"].toString()).set(d)

        // --- 5. FARECONFIG ---
        val fares = listOf(
            hashMapOf("Fare_ID" to 1, "Fare_VhclType" to "motorcycle", "Fare_Label" to "Motorcycle", "Fare_Emoji" to "\uD83D\uDEF5", "Fare_BaseFare" to 40.0, "Fare_PerKm" to 15.0, "Fare_IsActive" to true),
            hashMapOf("Fare_ID" to 2, "Fare_VhclType" to "sedan", "Fare_Label" to "200 kg Sedan", "Fare_Emoji" to "\uD83D\uDE97", "Fare_BaseFare" to 60.0, "Fare_PerKm" to 22.0, "Fare_IsActive" to true),
            hashMapOf("Fare_ID" to 3, "Fare_VhclType" to "suv_small", "Fare_Label" to "300 kg Subcompact SUV", "Fare_Emoji" to "\uD83D\uDE99", "Fare_BaseFare" to 70.0, "Fare_PerKm" to 26.0, "Fare_IsActive" to true),
            hashMapOf("Fare_ID" to 4, "Fare_VhclType" to "suv_large", "Fare_Label" to "600 kg 7-seater SUV", "Fare_Emoji" to "\uD83D\uDE90", "Fare_BaseFare" to 85.0, "Fare_PerKm" to 30.0, "Fare_IsActive" to true),
            hashMapOf("Fare_ID" to 5, "Fare_VhclType" to "van", "Fare_Label" to "1000 kg Closed Van", "Fare_Emoji" to "\uD83D\uDE9A", "Fare_BaseFare" to 110.0, "Fare_PerKm" to 38.0, "Fare_IsActive" to true),
            hashMapOf("Fare_ID" to 6, "Fare_VhclType" to "truck", "Fare_Label" to "2000 kg Large Truck", "Fare_Emoji" to "\uD83D\uDE9B", "Fare_BaseFare" to 160.0, "Fare_PerKm" to 52.0, "Fare_IsActive" to true)
        )
        for (f in fares) db.collection("fareconfig").document(f["Fare_ID"].toString()).set(f)

        // --- 6. SERVICE AREA ---
        val areas = listOf(
            hashMapOf("Area_ID" to 1, "Area_City" to "Cebu", "Area_region" to "Islandwide", "Area_zipcode" to null, "Area_IsActive" to true)
        )
        for (area in areas) db.collection("service_area").document(area["Area_ID"].toString()).set(area)

        // --- 7. VEHICLE ---
        val vehicles = listOf(
            hashMapOf("Vhcl_ID" to 2, "Vhcl_TypeID" to "motorcycle", "Vhcl_PlateNumber" to "KBK 2987", "Vhcl_Model" to "2015", "Vhcl_Year" to 2026, "Vhcl_Color" to "White", "Vhcl_Photo_OR" to "uploads/drivers/or_2_1778135591.jpg", "Vhcl_Photo_CR" to "uploads/drivers/cr_2_1778135591.jpg", "Vhcl_Status" to "verified")
        )
        for (v in vehicles) db.collection("vehicle").document(v["Vhcl_ID"].toString()).set(v)

        // --- 8. BOOKING ---
        val bookings = listOf(
            hashMapOf("Book_ID" to 1, "Book_CustID" to "1000142126", "Book_DrvrID" to "2000150726", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 199.00, "Book_Status" to "delivered", "Book_ScheduledTime" to "2026-05-08 18:16:00", "Book_CreatedAt" to "2026-04-27 18:14:07", "Book_Pickuploc" to "10.29797, 123.88694", "Book_Dropoffloc" to "10.32820, 123.92677", "Book_Notes" to null, "Book_AddServices" to null, "Book_IsRated" to true, "Book_RatingGiven" to 3),
            hashMapOf("Book_ID" to 2, "Book_CustID" to "1000142126", "Book_DrvrID" to "2000150726", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 429.00, "Book_Status" to "delivered", "Book_ScheduledTime" to "2026-04-27 18:40:00", "Book_CreatedAt" to "2026-04-27 18:40:49", "Book_Pickuploc" to "10.28344, 123.86720", "Book_Dropoffloc" to "10.37649, 123.95664", "Book_Notes" to null, "Book_AddServices" to null, "Book_IsRated" to true, "Book_RatingGiven" to 5),
            hashMapOf("Book_ID" to 3, "Book_CustID" to "1000142126", "Book_DrvrID" to "2000150726", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 221.00, "Book_Status" to "delivered", "Book_ScheduledTime" to "2026-04-27 18:55:00", "Book_CreatedAt" to "2026-04-27 18:55:49", "Book_Pickuploc" to "10.30118, 123.87527", "Book_Dropoffloc" to "10.32803, 123.92660", "Book_Notes" to null, "Book_AddServices" to null, "Book_IsRated" to true, "Book_RatingGiven" to null),
            hashMapOf("Book_ID" to 4, "Book_CustID" to "1000142126", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 130.00, "Book_Status" to "cancelled", "Book_ScheduledTime" to "2026-04-27 18:56:00", "Book_CreatedAt" to "2026-04-27 18:56:36", "Book_Pickuploc" to "10.31486, 123.91201", "Book_Dropoffloc" to "10.33233, 123.93149", "Book_Notes" to null, "Book_AddServices" to null, "Book_IsRated" to false, "Book_RatingGiven" to null),
            hashMapOf("Book_ID" to 5, "Book_CustID" to "1000142126", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 192.00, "Book_Status" to "cancelled", "Book_ScheduledTime" to "2026-04-27 19:32:00", "Book_CreatedAt" to "2026-04-27 19:33:12", "Book_Pickuploc" to "10.32769, 123.92840", "Book_Dropoffloc" to "10.30438, 123.88806", "Book_Notes" to "pLS HANDLE MY SHI", "Book_AddServices" to "Require driver to show Face and Driver app Profile", "Book_IsRated" to false, "Book_RatingGiven" to null),
            hashMapOf("Book_ID" to 6, "Book_CustID" to "1000142126", "Book_DrvrID" to "2000150726", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 254.00, "Book_Status" to "cancelled", "Book_ScheduledTime" to "2026-04-27 19:49:00", "Book_CreatedAt" to "2026-04-27 19:49:40", "Book_Pickuploc" to "10.30050, 123.88875", "Book_Dropoffloc" to "10.32094, 123.91793", "Book_Notes" to "hello", "Book_AddServices" to "Extra waiting time, Require driver to show Face and Driver app Profile", "Book_IsRated" to false, "Book_RatingGiven" to null),
            hashMapOf("Book_ID" to 7, "Book_CustID" to "1000142126", "Book_DrvrID" to "2000150726", "Book_VhclTypeID" to "motorcycle", "Book_TotalFare" to 177.00, "Book_Status" to "delivered", "Book_ScheduledTime" to "2026-05-07 14:22:04", "Book_CreatedAt" to "2026-05-07 20:22:04", "Book_Pickuploc" to "10.29738, 123.90403", "Book_Dropoffloc" to "10.32811, 123.93338", "Book_Notes" to "", "Book_AddServices" to "Extra waiting time", "Book_IsRated" to true, "Book_RatingGiven" to 1)
        )
        for (b in bookings) db.collection("booking").document(b["Book_ID"].toString()).set(b)

        // --- 9. DELIVERY ---
        val deliveries = listOf(
            hashMapOf("Dlvr_ID" to 1, "Dlvr_BookID" to 1, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.32820, 123.92677", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "completed", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 2, "Dlvr_BookID" to 2, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.37649, 123.95664", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "completed", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 3, "Dlvr_BookID" to 3, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.32803, 123.92660", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "completed", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 4, "Dlvr_BookID" to 4, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.33233, 123.93149", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "pending", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 5, "Dlvr_BookID" to 5, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.30438, 123.88806", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "pending", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 6, "Dlvr_BookID" to 6, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.32094, 123.91793", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "completed", "Dlvr_ProofOfDelivery" to null),
            hashMapOf("Dlvr_ID" to 7, "Dlvr_BookID" to 7, "Dlvr_StopNumber" to 1, "Dlvr_Address" to "10.32811, 123.93338", "Dlvr_ContactName" to null, "Dlvr_ContactPhone" to null, "Dlvr_Status" to "completed", "Dlvr_ProofOfDelivery" to null)
        )
        for (dl in deliveries) db.collection("delivery").document(dl["Dlvr_ID"].toString()).set(dl)

        // --- 10. ITEM ---
        val items = listOf(
            hashMapOf("Item_ID" to 1, "Item_DlvrID" to 1, "Item_Name" to "Ipad Touch", "Item_Category" to "Electronics and gadgets", "Item_Quantity" to 1, "Item_Size" to "Small", "Item_WeightKG" to 4.99, "Item_Photo" to null),
            hashMapOf("Item_ID" to 2, "Item_DlvrID" to 2, "Item_Name" to "Package", "Item_Category" to "", "Item_Quantity" to 0, "Item_Size" to "Select size", "Item_WeightKG" to 0.0, "Item_Photo" to null),
            hashMapOf("Item_ID" to 3, "Item_DlvrID" to 3, "Item_Name" to "Package", "Item_Category" to "", "Item_Quantity" to 0, "Item_Size" to "Select size", "Item_WeightKG" to 0.0, "Item_Photo" to null),
            hashMapOf("Item_ID" to 4, "Item_DlvrID" to 4, "Item_Name" to "Ipod MAX", "Item_Category" to "Electronics and gadgets, Others", "Item_Quantity" to 1, "Item_Size" to "Small", "Item_WeightKG" to 4.99, "Item_Photo" to null),
            hashMapOf("Item_ID" to 5, "Item_DlvrID" to 5, "Item_Name" to "office desk", "Item_Category" to "Others", "Item_Quantity" to 1, "Item_Size" to "Small", "Item_WeightKG" to 4.99, "Item_Photo" to null),
            hashMapOf("Item_ID" to 6, "Item_DlvrID" to 6, "Item_Name" to "Package", "Item_Category" to "", "Item_Quantity" to 0, "Item_Size" to "Select size", "Item_WeightKG" to 0.0, "Item_Photo" to null),
            hashMapOf("Item_ID" to 7, "Item_DlvrID" to 7, "Item_Name" to "Package", "Item_Category" to "", "Item_Quantity" to 0, "Item_Size" to "Select size", "Item_WeightKG" to 0.0, "Item_Photo" to null)
        )
        for (i in items) db.collection("item").document(i["Item_ID"].toString()).set(i)

        // --- 11. TRANSACTIONS ---
        val transactions = listOf(
            hashMapOf("Tran_ID" to 1, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "earnings", "Tran_Amount" to 203.20, "Tran_ReferenceNum" to "BOOK-6", "Tran_Date" to "2026-05-07 14:34:59"),
            hashMapOf("Tran_ID" to 2, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "earnings", "Tran_Amount" to 176.80, "Tran_ReferenceNum" to "BOOK-3", "Tran_Date" to "2026-05-07 18:22:45"),
            hashMapOf("Tran_ID" to 3, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "earnings", "Tran_Amount" to 343.20, "Tran_ReferenceNum" to "BOOK-2", "Tran_Date" to "2026-05-07 20:02:09"),
            hashMapOf("Tran_ID" to 4, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "earnings", "Tran_Amount" to 159.20, "Tran_ReferenceNum" to "BOOK-1", "Tran_Date" to "2026-05-07 20:21:35"),
            hashMapOf("Tran_ID" to 5, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "earnings", "Tran_Amount" to 141.60, "Tran_ReferenceNum" to "BOOK-7", "Tran_Date" to "2026-05-07 20:22:14"),
            hashMapOf("Tran_ID" to 6, "Tran_TargetType" to "driver", "Tran_TargetID" to 2000150726L, "Tran_Type" to "deduction", "Tran_Amount" to 500.00, "Tran_ReferenceNum" to "CASHOUT-1778156573", "Tran_Date" to "2026-05-07 20:22:53")
        )
        for (t in transactions) db.collection("transaction").document(t["Tran_ID"].toString()).set(t)

        // --- 12. MOVEMENTLOG ---
        val movements = listOf(
            hashMapOf("Move_ID" to 1, "Move_BookID" to 6, "Move_Status" to "driver_en_route", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 14:34:52"),
            hashMapOf("Move_ID" to 2, "Move_BookID" to 6, "Move_Status" to "picked_up", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 14:34:55"),
            hashMapOf("Move_ID" to 3, "Move_BookID" to 6, "Move_Status" to "delivered", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 14:34:59"),
            hashMapOf("Move_ID" to 4, "Move_BookID" to 3, "Move_Status" to "driver_en_route", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 18:22:39"),
            hashMapOf("Move_ID" to 5, "Move_BookID" to 3, "Move_Status" to "picked_up", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 18:22:41"),
            hashMapOf("Move_ID" to 6, "Move_BookID" to 3, "Move_Status" to "delivered", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 18:22:45"),
            hashMapOf("Move_ID" to 7, "Move_BookID" to 2, "Move_Status" to "driver_en_route", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:02:05"),
            hashMapOf("Move_ID" to 8, "Move_BookID" to 2, "Move_Status" to "picked_up", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:02:06"),
            hashMapOf("Move_ID" to 9, "Move_BookID" to 2, "Move_Status" to "delivered", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:02:09"),
            hashMapOf("Move_ID" to 10, "Move_BookID" to 1, "Move_Status" to "driver_en_route", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:21:31"),
            hashMapOf("Move_ID" to 11, "Move_BookID" to 1, "Move_Status" to "picked_up", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:21:33"),
            hashMapOf("Move_ID" to 12, "Move_BookID" to 1, "Move_Status" to "delivered", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:21:35"),
            hashMapOf("Move_ID" to 13, "Move_BookID" to 7, "Move_Status" to "driver_en_route", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:22:10"),
            hashMapOf("Move_ID" to 14, "Move_BookID" to 7, "Move_Status" to "picked_up", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:22:12"),
            hashMapOf("Move_ID" to 15, "Move_BookID" to 7, "Move_Status" to "delivered", "Move_Latitude" to null, "Move_Longitude" to null, "Move_LoggedAt" to "2026-05-07 20:22:14")
        )
        for (m in movements) db.collection("movementlog").document(m["Move_ID"].toString()).set(m)

        // --- 13. BUSINESS PROFILE (Placeholder) ---
        db.collection("business_profile").document("1").set(hashMapOf("Bsns_ID" to 1, "Bsns_CompanyName" to "Lalamove Partner", "Bsns_Industry" to "Logistics", "Bsns_JobTitle" to "Manager", "Bsns_Address" to "Cebu City", "Bsns_RegistrationNum" to "REG-123", "Bsns_City" to "Cebu", "Bsns_EstDeliveryVol" to 100, "Bsns_FrequentlyUsedVehicle" to "motorcycle", "Bsns_PrefferedContact" to "email", "Bsns_CertificatePhoto" to null, "Bsns_CustID" to 1000142126L))

        // --- 14. COUPON (Example) ---
        db.collection("coupon").document("LALA50").set(hashMapOf("Coup_ID" to 1, "Coup_Code" to "LALA50", "Coup_DiscountValue" to 50.0, "Coup_Type" to "fixed", "Coup_MinSpend" to 200.0, "Coup_ExpiryDate" to "2026-12-31", "Coup_IsActive" to true))

        // --- 15. DISPUTE (Placeholder) ---
        db.collection("dispute").document("1").set(hashMapOf("Disp_ID" to 1, "Disp_BookID" to 1, "Disp_ReporterType" to "customer", "Disp_ReporterID" to 1000142126L, "Disp_Subject" to "Late Delivery", "Disp_Details" to "Driver arrived 30 mins late", "Disp_Status" to "open", "Disp_Resolution" to null, "Disp_CreatedAt" to "2026-05-07 20:24:05", "Disp_ResolvedAt" to null))

        // --- 16. PAYMENT (Placeholder) ---
        db.collection("payment").document("1").set(hashMapOf("Paym_ID" to 1, "Paym_BookID" to 1, "Paym_CoupID" to null, "Paym_Method" to "cash", "Paym_AmountPaid" to 199.0, "Paym_Status" to "completed", "Paym_PaidAt" to "2026-05-07 20:21:35"))

        Log.d("DatabaseSeeder", "Database fully synchronized with SQL export!")
    }
}
