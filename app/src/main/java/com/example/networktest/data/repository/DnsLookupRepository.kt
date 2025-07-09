package com.example.networktest.data.repository

import com.example.networktest.data.model.DnsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
// For more advanced DNS lookups (MX, TXT etc.), a third-party library like dnsjava would be needed.
// For this example, we'll stick to what InetAddress provides (A, AAAA records primarily).

class DnsLookupRepository @Inject constructor() {

    fun lookupDns(hostname: String): Flow<List<DnsRecord>> = flow {
        if (hostname.isBlank()) {
            emit(listOf(DnsRecord(name = hostname, type = "ERROR", ttl = null, value = "", error = "Hostname cannot be empty")))
            return@flow
        }

        val records = mutableListOf<DnsRecord>()

        try {
            // This primarily gets A and AAAA records.
            val addresses: Array<InetAddress> = InetAddress.getAllByName(hostname)
            var canonicalName: String? = null
            try {
                 canonicalName = InetAddress.getByName(hostname).canonicalHostName
                 if (canonicalName != null && canonicalName != hostname && !addresses.any{ it.hostName == canonicalName}) {
                    // Check if canonical name is different and not already part of addresses (which can happen)
                    // This suggests a CNAME record pointed to this canonical name.
                    // The actual CNAME record itself isn't directly exposed by InetAddress,
                    // but we can infer its existence.
                    records.add(DnsRecord(name = hostname, type = "CNAME", ttl = null, value = canonicalName))
                 }
            } catch (e: UnknownHostException) {
                // This might happen if the initial hostname itself cannot be resolved at all.
                // The outer try-catch will handle the main error.
            } catch (e: Exception){
                // Other errors while getting canonical name
            }


            if (addresses.isEmpty()) {
                records.add(DnsRecord(name = hostname, type = "ERROR", ttl = null, value = "", error = "No IP addresses found for hostname."))
            } else {
                addresses.forEach { address ->
                    val type = when {
                        address.hostAddress.contains(':') -> "AAAA" // IPv6
                        else -> "A" // IPv4
                    }
                    // InetAddress doesn't directly provide TTL.
                    // It might be possible to get it using reflection on internal Android DNS resolver caches
                    // or by using a dedicated DNS library, but that's beyond simple InetAddress.
                    records.add(
                        DnsRecord(
                            name = address.hostName ?: hostname, // Use address.hostName if available (might be the same as input or resolved)
                            type = type,
                            ttl = null, // TTL not available via InetAddress
                            value = address.hostAddress
                        )
                    )
                }
            }
            emit(records.distinctBy { it.type + it.value }) // Ensure unique records if CNAME and A record point to same hostname string

        } catch (e: UnknownHostException) {
            records.add(DnsRecord(name = hostname, type = "ERROR", ttl = null, value = "", error = "Unknown host: ${e.message}"))
            emit(records)
        } catch (e: Exception) {
            records.add(DnsRecord(name = hostname, type = "ERROR", ttl = null, value = "", error = "DNS lookup failed: ${e.message}"))
            emit(records)
        }
    }.flowOn(Dispatchers.IO)

    // Note: To get other DNS record types like MX, TXT, NS, SRV, etc.,
    // a more specialized DNS library is required (e.g., dnsjava/DNSJava or similar).
    // The standard Java InetAddress class is limited mostly to A and AAAA record lookups.
    // For a comprehensive tool, integrating such a library would be the next step for DNS features.
    // Example (conceptual) for MX records if using a library:
    /*
    fun lookupMxRecords(hostname: String): Flow<List<DnsRecord>> = flow {
        // ... setup library ...
        // val lookup = Lookup(hostname, Type.MX)
        // val result = lookup.run()
        // if (result != null) {
        //     result.records.forEach { record ->
        //         if (record is MXRecord) {
        //             records.add(DnsRecord(name=hostname, type="MX", ttl=record.ttl, value="${record.priority} ${record.target}"))
        //         }
        //     }
        // }
        // emit(records)
    }.flowOn(Dispatchers.IO)
    */
}
