import com.cloudtown.game.MapCamera;

public final class MapCameraTest {
    private static int checks;
    private static void near(float a,float b) {
        checks++;if(Math.abs(a-b)>.003f)throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        MapCamera cam=new MapCamera();
        near(cam.screenX(3,2,38),-19);near(cam.screenY(3,2,19),-28.5f);
        for(float angle:new float[]{0,.2f,.785398f,1.57f,2.8f,-3.14f,4.8f}) {
            cam.restore(2.3f,angle,70,-25);
            for(int r=0;r<9;r++)for(int c=0;c<9;c++) {
                float x=cam.screenX(r,c,38),y=cam.screenY(r,c,19);
                near(cam.row(x,y,38,19),r);near(cam.column(x,y,38,19),c);
            }
        }
        cam.reset();float r=cam.row(30,-15,38,19),c=cam.column(30,-15,38,19);
        cam.gesture(30,-15,47,-9,1.8f,.7f,38,19);
        near(cam.screenX(r,c,38),47);near(cam.screenY(r,c,19),-9);
        cam.gesture(47,-9,47,-9,100,0,38,19);near(cam.zoom,3);
        cam.gesture(47,-9,47,-9,.0001f,0,38,19);near(cam.zoom,.7f);
        cam.pan(100000,-100000);near(cam.panX,105);near(cam.panY,-63);
        cam.restore(Float.NaN,0,0,0);near(cam.zoom,1);near(cam.panX,0);
        cam.restore(2,Float.POSITIVE_INFINITY,0,0);near(cam.zoom,1);
        cam.restore(1,3.13f,0,0);cam.gesture(0,0,0,0,1,.04f,38,19);
        near(cam.angle,3.17f-(float)(2*Math.PI));
        System.out.println("PASS: "+checks+" camera projection and gesture checks");
    }
}
