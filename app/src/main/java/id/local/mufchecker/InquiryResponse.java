package id.local.mufchecker;

/** Only allowlisted messages survive a snapshot/event. No raw customer text is retained. */
public final class InquiryResponse {
    public static final long NO_RESPONSE_MS=10000, EVENT_FRESH_MS=5000, MAX_QUIET_GAP_MS=2000;
    public enum Signal {
        NONE("",false),
        RESTRICTED("Pembayaran dibatasi — hubungi kantor cabang.",true),
        CONTRACT_NOT_FOUND("Tokopedia menyatakan nomor kontrak/pelanggan tidak ditemukan atau tidak valid.",true),
        NO_BILL("Tokopedia menyatakan tagihan tidak ditemukan atau tidak ada tagihan. Status bayar belum disimpulkan.",true),
        ALREADY_PAID("Tokopedia menampilkan pesan tagihan sudah dibayar tanpa rincian terverifikasi. Status bayar belum disimpulkan.",true),
        SERVICE_ERROR("Tokopedia menampilkan kesalahan layanan atau meminta mencoba lagi nanti. Status belum dapat disimpulkan.",false);
        public final String reason;
        public final boolean contractError;
        Signal(String reason,boolean contractError){this.reason=reason;this.contractError=contractError;}
    }
    private Signal event=Signal.NONE;
    private long eventCapturedAt=-1,quietSince=-1,lastQuietObservation=-1;
    public static boolean accepted(InquirySession.TapState state){
        return state==InquirySession.TapState.DISPATCHED || state==InquirySession.TapState.COMPLETED;
    }
    public static Signal classify(String text){
        String value=InquiryRules.normalized(text).replaceAll("[.,;:!?]"," ").replaceAll("\\s+"," ");
        if(FlowSupport.branchRestriction(value))return Signal.RESTRICTED;
        if(contains(value,"nomor kontrak tidak ditemukan","nomor pelanggan tidak ditemukan","nomor kontrak tidak valid","nomor pelanggan tidak valid"))return Signal.CONTRACT_NOT_FOUND;
        if(contains(value,"tagihan tidak ditemukan","tidak ada tagihan"))return Signal.NO_BILL;
        if(value.contains("tagihan sudah dibayar"))return Signal.ALREADY_PAID;
        if(contains(value,"terjadi kesalahan","coba lagi nanti","layanan sedang tidak tersedia","gangguan jaringan"))return Signal.SERVICE_ERROR;
        return Signal.NONE;
    }
    private static boolean contains(String value,String... phrases){for(String phrase:phrases)if(value.contains(phrase))return true;return false;}
    public static Signal prefer(Signal first,Signal next){
        if(first==Signal.RESTRICTED || next==Signal.NONE)return first;
        if(next==Signal.RESTRICTED || first==Signal.NONE || !first.contractError && next.contractError)return next;
        return first;
    }
    public void observeEvent(boolean ownPackage,boolean active,InquirySession session,long submittedAt,long eventAt,long now,String text){
        if(!ownPackage || session==null || session.stage()!=InquirySession.Stage.WAIT_RESULT || !accepted(session.tapState())
            || !FlowSupport.belongsToInquiry(active,session.submitted(),session.resultObserved(),submittedAt,eventAt,now))return;
        Signal next=classify(text);if(next==Signal.NONE)return;
        event=prefer(pendingEvent(now),next);eventCapturedAt=now;
    }
    public Signal pendingEvent(long now){
        if(eventCapturedAt<0 || now<eventCapturedAt || now-eventCapturedAt>EVENT_FRESH_MS){event=Signal.NONE;eventCapturedAt=-1;}
        return event;
    }
    /** Requires repeated, uninterrupted observations of the exact, idle form after one accepted inquiry. */
    public boolean observeQuiet(long now,InquirySession session,boolean exactForm,boolean loading,boolean checkout){
        boolean eligible=session!=null && session.stage()==InquirySession.Stage.WAIT_RESULT && session.submitted()
            && accepted(session.tapState()) && !session.resultObserved() && exactForm && !loading && !checkout;
        if(!eligible){resetQuiet();return false;}
        if(lastQuietObservation<0 || now<lastQuietObservation || now-lastQuietObservation>MAX_QUIET_GAP_MS)quietSince=now;
        lastQuietObservation=now;
        return now-quietSince>=NO_RESPONSE_MS;
    }
    public long quietAge(long now){return quietSince<0?0:Math.max(0,now-quietSince);}
    public long deadline(){return quietSince<0?-1:quietSince+NO_RESPONSE_MS;}
    public void resetQuiet(){quietSince=-1;lastQuietObservation=-1;}
    public void reset(){resetQuiet();event=Signal.NONE;eventCapturedAt=-1;}
}
