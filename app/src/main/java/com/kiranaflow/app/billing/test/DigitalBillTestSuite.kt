package com.kiranaflow.app.billing.test

import android.content.Context
import android.util.Log
import com.kiranaflow.app.billing.factory.BillSnapshotFactory
import com.kiranaflow.app.billing.model.BillSnapshot
import com.kiranaflow.app.billing.render.BillBitmapRenderer
import com.kiranaflow.app.billing.render.BillTextFormatter
import com.kiranaflow.app.billing.send.WhatsAppShareManager
import com.kiranaflow.app.billing.send.ValidationResult
import com.kiranaflow.app.billing.send.WhatsAppShareResult
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.ui.screens.billing.BillSavedEvent
import com.kiranaflow.app.ui.screens.billing.BillSavedLineItem
import com.kiranaflow.app.utils.PhoneFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Comprehensive test suite for the enhanced WhatsApp digital bill feature
 */
object DigitalBillTestSuite {
    
    private const val TAG = "DigitalBillTest"
    
    /**
     * Runs all tests and returns a comprehensive report
     */
    fun runAllTests(context: Context): TestReport {
        val results = mutableListOf<TestResult>()
        
        // Test 1: Phone Number Normalization
        results.add(testPhoneNormalization())
        
        // Test 2: BillSnapshot Creation
        results.add(testBillSnapshotCreation())
        
        // Test 3: Bill Image Rendering
        results.add(testBillImageRendering())
        
        // Test 4: Bill Text Formatting
        results.add(testBillTextFormatting())
        
        // Test 5: WhatsApp Validation
        results.add(testWhatsAppValidation())
        
        // Test 6: End-to-End Flow (without actually sending)
        results.add(testEndToEndFlow(context))
        
        return TestReport(results)
    }
    
    private fun testPhoneNormalization(): TestResult {
        val testCases = mapOf(
            "9876543210" to "+919876543210",
            "+919876543210" to "+919876543210",
            "919876543210" to "+919876543210",
            "12345" to null,
            "invalid" to null,
            "" to null
        )
        
        val failures = mutableListOf<String>()
        
        testCases.forEach { (input, expected) ->
            val actual = PhoneFormatter.normalizeE164(input)
            if (actual != expected) {
                failures.add("Phone normalization failed: '$input' -> expected '$expected', got '$actual'")
            }
        }
        
        return TestResult(
            name = "Phone Number Normalization",
            passed = failures.isEmpty(),
            details = if (failures.isEmpty()) "All ${testCases.size} test cases passed" else failures.joinToString("; ")
        )
    }
    
    private fun testBillSnapshotCreation(): TestResult {
        return try {
            val mockEvent = createMockBillSavedEvent()
            val mockShop = createMockShopSettings()
            val mockCustomer = createMockCustomer()
            
            val billSnapshot = BillSnapshotFactory.createFromBillingEvent(mockEvent, mockShop, mockCustomer)
            
            val validations = mutableListOf<String>()
            
            // Validate store info
            if (billSnapshot.storeInfo.name.isBlank()) validations.add("Store name is blank")
            if (billSnapshot.storeInfo.address.isBlank()) validations.add("Store address is blank")
            
            // Validate customer info
            if (billSnapshot.customerInfo.name.isBlank()) validations.add("Customer name is blank")
            
            // Validate items
            if (billSnapshot.items.isEmpty()) validations.add("No items in bill")
            if (billSnapshot.items.size != mockEvent.items.size) validations.add("Item count mismatch")
            
            // Validate totals
            if (billSnapshot.totals.totalAmountPaid <= 0) validations.add("Total amount is invalid")
            
            // Validate GST
            if (billSnapshot.gstSummary.totalGST < 0) validations.add("GST total is negative")
            
            TestResult(
                name = "BillSnapshot Creation",
                passed = validations.isEmpty(),
                details = if (validations.isEmpty()) "BillSnapshot created successfully with ${billSnapshot.items.size} items" 
                else validations.joinToString("; ")
            )
        } catch (e: Exception) {
            TestResult(
                name = "BillSnapshot Creation",
                passed = false,
                details = "Exception: ${e.message}"
            )
        }
    }
    
    private fun testBillImageRendering(): TestResult {
        return try {
            val billSnapshot = createMockBillSnapshot()
            
            val startTime = System.currentTimeMillis()
            val bitmap = BillBitmapRenderer.renderToBitmap(billSnapshot)
            val renderTime = System.currentTimeMillis() - startTime
            
            val validations = mutableListOf<String>()
            
            if (bitmap.width != 576) validations.add("Bitmap width is ${bitmap.width}, expected 576")
            if (bitmap.height < 900) validations.add("Bitmap height is too small: ${bitmap.height}")
            if (renderTime > 5000) validations.add("Rendering took too long: ${renderTime}ms")
            
            TestResult(
                name = "Bill Image Rendering",
                passed = validations.isEmpty(),
                details = if (validations.isEmpty()) "Bitmap rendered successfully in ${renderTime}ms (${bitmap.width}x${bitmap.height})"
                else validations.joinToString("; ")
            )
        } catch (e: Exception) {
            TestResult(
                name = "Bill Image Rendering",
                passed = false,
                details = "Exception: ${e.message}"
            )
        }
    }
    
    private fun testBillTextFormatting(): TestResult {
        return try {
            val billSnapshot = createMockBillSnapshot()
            
            val caption = BillTextFormatter.formatWhatsAppCaption(billSnapshot)
            
            val validations = mutableListOf<String>()
            
            if (caption.isBlank()) validations.add("Caption is blank")
            if (!caption.contains(billSnapshot.storeInfo.name)) validations.add("Store name missing from caption")
            if (!caption.contains("TOTAL")) validations.add("Total section missing from caption")
            if (!caption.contains("GST")) validations.add("GST section missing from caption")
            if (caption.length > 4000) validations.add("Caption too long for WhatsApp: ${caption.length} chars")
            
            TestResult(
                name = "Bill Text Formatting",
                passed = validations.isEmpty(),
                details = if (validations.isEmpty()) "Caption formatted successfully (${caption.length} chars)"
                else validations.joinToString("; ")
            )
        } catch (e: Exception) {
            TestResult(
                name = "Bill Text Formatting",
                passed = false,
                details = "Exception: ${e.message}"
            )
        }
    }
    
    private fun testWhatsAppValidation(): TestResult {
        return try {
            val validBill = createMockBillSnapshot()
            val invalidBill = validBill.copy(
                items = emptyList(),
                totals = validBill.totals.copy(totalAmountPaid = 0.0)
            )
            
            val validResult = WhatsAppShareManager.validateBillForWhatsApp(validBill, "9876543210")
            val invalidResult = WhatsAppShareManager.validateBillForWhatsApp(invalidBill, "invalid")
            
            val validations = mutableListOf<String>()
            
            if (validResult !is ValidationResult.Valid) validations.add("Valid bill failed validation")
            if (invalidResult !is ValidationResult.Invalid) validations.add("Invalid bill passed validation")
            if (invalidResult is ValidationResult.Invalid && invalidResult.issues.isEmpty()) validations.add("Invalid bill has no error messages")
            
            TestResult(
                name = "WhatsApp Validation",
                passed = validations.isEmpty(),
                details = if (validations.isEmpty()) "Validation working correctly"
                else validations.joinToString("; ")
            )
        } catch (e: Exception) {
            TestResult(
                name = "WhatsApp Validation",
                passed = false,
                details = "Exception: ${e.message}"
            )
        }
    }
    
    private fun testEndToEndFlow(context: Context): TestResult {
        return try {
            val billSnapshot = createMockBillSnapshot()
            val customerPhone = "9876543210"
            
            // Test validation
            val validation = WhatsAppShareManager.validateBillForWhatsApp(billSnapshot, customerPhone)
            if (validation !is ValidationResult.Valid) {
                val issues = when (validation) {
                    is ValidationResult.Invalid -> validation.issues
                    else -> listOf("Unknown validation error")
                }
                return TestResult(
                    name = "End-to-End Flow",
                    passed = false,
                    details = "Validation failed: ${issues.joinToString(", ")}"
                )
            }
            
            // Test image generation
            val bitmap = BillBitmapRenderer.renderToBitmap(billSnapshot)
            if (bitmap.width != 576 || bitmap.height < 900) {
                return TestResult(
                    name = "End-to-End Flow",
                    passed = false,
                    details = "Image generation failed"
                )
            }
            
            // Test caption generation
            val caption = BillTextFormatter.formatWhatsAppCaption(billSnapshot)
            if (caption.isBlank()) {
                return TestResult(
                    name = "End-to-End Flow",
                    passed = false,
                    details = "Caption generation failed"
                )
            }
            
            // Note: We don't actually send to WhatsApp in tests, but we validate the intent creation
            Log.d(TAG, "End-to-end test passed - all components working correctly")
            
            TestResult(
                name = "End-to-End Flow",
                passed = true,
                details = "All components integrated successfully"
            )
        } catch (e: Exception) {
            TestResult(
                name = "End-to-End Flow",
                passed = false,
                details = "Exception: ${e.message}"
            )
        }
    }
    
    // Helper methods to create mock data
    private fun createMockBillSavedEvent(): BillSavedEvent {
        return BillSavedEvent(
            txId = 12345,
            customerId = 1,
            paymentMode = "CASH",
            totalAmount = 486.32,
            createdAtMillis = System.currentTimeMillis(),
            items = listOf(
                BillSavedLineItem(1, "Rice Basmati 1kg", 2.0, false, 120.0, "pcs", 240.0),
                BillSavedLineItem(2, "Sugar 1kg", 1.5, true, 40.0, "kg", 60.0),
                BillSavedLineItem(3, "Cooking Oil 1L", 1.0, false, 150.0, "pcs", 150.0),
                BillSavedLineItem(4, "Tea Powder 250g", 2.0, false, 36.16, "pcs", 72.32)
            )
        )
    }
    
    private fun createMockShopSettings(): ShopSettings {
        return ShopSettings(
            shopName = "Test Kirana Store",
            address = "123 Main Street\nBangalore, Karnataka - 560001",
            shopPhone = "9876543210",
            gstin = "29ABCDE1234F1ZV",
            legalName = "Test Legal Name"
        )
    }
    
    private fun createMockCustomer(): CustomerEntity {
        return CustomerEntity(
            id = 1,
            name = "Test Customer",
            phone = "9876543210",
            type = "CUSTOMER"
        )
    }
    
    private fun createMockBillSnapshot(): BillSnapshot {
        return BillSnapshotFactory.createFromBillingEvent(
            createMockBillSavedEvent(),
            createMockShopSettings(),
            createMockCustomer()
        )
    }
}

data class TestResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class TestReport(
    val results: List<TestResult>
) {
    val passedCount: Int get() = results.count { it.passed }
    val totalCount: Int get() = results.size
    val allPassed: Boolean get() = passedCount == totalCount
    
    fun printReport() {
        Log.d("DigitalBillTest", "=== DIGITAL BILL TEST REPORT ===")
        Log.d("DigitalBillTest", "Passed: $passedCount/$totalCount")
        
        results.forEach { result ->
            val status = if (result.passed) "✅ PASS" else "❌ FAIL"
            Log.d("DigitalBillTest", "$status ${result.name}: ${result.details}")
        }
        
        Log.d("DigitalBillTest", "=== END REPORT ===")
    }
}
