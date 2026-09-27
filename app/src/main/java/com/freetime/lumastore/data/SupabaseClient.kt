package com.freetime.lumastore.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

val supabase = createSupabaseClient(
    supabaseUrl = "https://ndlaevedujqxhygbyxfh.supabase.co",
    supabaseKey = "sb_publishable_HlppI4ILiXV7DZkpyrDEhQ_ytb2vV6g"
) {
    install(Postgrest)
    install(Storage)
    install(Auth) {
        scheme = "lumastore"
        host = "auth"
    }
}
