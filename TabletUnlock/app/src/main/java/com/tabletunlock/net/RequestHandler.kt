package com.tabletunlock.net

/** معالج طلب وارد على قناة، مستقل عن النقل (بلوتوث/واي فاي) والجهاز. */
interface RequestHandler {
    suspend fun handle(link: Link, onLog: (String) -> Unit)
}
