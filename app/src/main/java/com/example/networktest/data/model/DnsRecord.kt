package com.example.networktest.data.model

data class DnsRecord(
    val name: String, // The queried hostname
    val type: String, // e.g., A, AAAA, CNAME, MX, TXT, NS
    val ttl: Int?, // Time to Live (often not directly available from InetAddress)
    val value: String, // The record value (e.g., IP address, another hostname)
    val error: String? = null // If there was an error resolving this specific record type or name
)
