package com.freetime.lumastore.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

private const val SUPABASE_URL = "https://ndlaevedujqxhygbyxfh.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_HlppI4ILiXV7DZkpyrDEhQ_ytb2vV6g"

// Public aggregates must use the same anonymous RLS role regardless of account state.
internal val publicStoreSupabase = createSupabaseClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY) {
    install(Postgrest)
}

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY
) {
    install(Postgrest)
    install(Storage)
    install(Auth) {
        scheme = "lumastore"
        host = "auth"
    }
}
