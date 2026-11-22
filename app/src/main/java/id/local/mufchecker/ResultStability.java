package id.local.mufchecker;

/** Only already identity/date-verified snapshots enter this guard. Gaps or changes reset it. */
public final class ResultStability {
    private String digest="";
    private long first=-1,last=-1;
    private int reads;
    public void reset(){digest="";first=last=-1;reads=0;}
    public long deadline(){return reads==0?-1:first+FlowTiming.RESULT_STABLE_MS;}
    public boolean observe(String verifiedDigest,long now){
        if(verifiedDigest==null||verifiedDigest.isEmpty()){reset();return false;}
        if(!verifiedDigest.equals(digest)||last>now){digest=verifiedDigest;first=now;last=now;reads=1;return false;}
        if(now<=last)return false;
        reads++;last=now;
        return reads>=2&&now-first>=FlowTiming.RESULT_STABLE_MS;
    }
}
