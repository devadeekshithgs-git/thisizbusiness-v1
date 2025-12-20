package com.kiranaflow.app.data.local

// Type aliases for backward compatibility with UI code
// The database uses PartyEntity with a 'type' field, but UI code references CustomerEntity/VendorEntity
typealias CustomerEntity = PartyEntity
typealias VendorEntity = PartyEntity

