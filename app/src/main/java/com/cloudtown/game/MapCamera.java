package com.cloudtown.game;

/** Camera coordinates are relative to the island centre, in logical screen units. */
public final class MapCamera {
    public float zoom = 1, angle, panX, panY;
    public void reset() { zoom=1;angle=panX=panY=0; }
    public void restore(float zoom,float angle,float panX,float panY) {
        if(!finite(zoom)||!finite(angle)||!finite(panX)||!finite(panY)) { reset();return; }
        this.zoom=zoom;this.angle=angle;this.panX=panX;this.panY=panY;constrain();
    }
    private boolean finite(float v) { return !Float.isNaN(v)&&!Float.isInfinite(v); }
    private void constrain() {
        zoom=Math.max(.7f,Math.min(3f,zoom));
        angle=(float)Math.atan2(Math.sin(angle),Math.cos(angle));
        panX=Math.max(-150*zoom,Math.min(150*zoom,panX));
        panY=Math.max(-90*zoom,Math.min(90*zoom,panY));
    }
    public float x(float r,float c,float tileW) {
        double co=Math.cos(angle),si=Math.sin(angle);
        float rr=r-4,cc=c-4;
        return (float)((cc*co-rr*si)-(cc*si+rr*co))*tileW/2;
    }
    public float y(float r,float c,float tileH) {
        double co=Math.cos(angle),si=Math.sin(angle);
        float rr=r-4,cc=c-4;
        return (float)((cc*co-rr*si)+(cc*si+rr*co))*tileH/2;
    }
    public float screenX(float r,float c,float tileW) { return panX+zoom*x(r,c,tileW); }
    public float screenY(float r,float c,float tileH) { return panY+zoom*y(r,c,tileH); }
    public float column(float x,float y,float tileW,float tileH) {
        float dx=(x-panX)/zoom/(tileW/2),dy=(y-panY)/zoom/(tileH/2);
        float cc=(dx+dy)/2,rr=(dy-dx)/2;
        return 4+(float)(cc*Math.cos(angle)+rr*Math.sin(angle));
    }
    public float row(float x,float y,float tileW,float tileH) {
        float dx=(x-panX)/zoom/(tileW/2),dy=(y-panY)/zoom/(tileH/2);
        float cc=(dx+dy)/2,rr=(dy-dx)/2;
        return 4+(float)(-cc*Math.sin(angle)+rr*Math.cos(angle));
    }
    public void pan(float dx,float dy) { panX+=dx;panY+=dy;constrain(); }
    public void gesture(float oldX,float oldY,float newX,float newY,float ratio,float rotation,float tileW,float tileH) {
        if(!finite(ratio)||ratio<=0||!finite(rotation))return;
        float r=row(oldX,oldY,tileW,tileH),c=column(oldX,oldY,tileW,tileH);
        zoom*=ratio;angle+=rotation;constrain();
        panX=newX-zoom*x(r,c,tileW);panY=newY-zoom*y(r,c,tileH);constrain();
    }
}
