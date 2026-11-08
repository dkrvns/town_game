import com.cloudtown.game.GameState;

public final class GameStateTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++; if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args) {
        GameState s=new GameState();
        check(s.population()==4 && s.coins==400,"initial city");
        String snapshot=s.encode();
        check(GameState.decode(snapshot).encode().equals(snapshot),"save roundtrip");
        check(s.build(0,0,GameState.HOUSE)!=null,"road requirement");
        check(s.build(3,2,GameState.HOUSE)==null,"valid construction");
        check(s.coins==310 && s.population()==8,"construction accounting");
        check(s.build(3,2,GameState.SHOP)!=null && s.coins==310,"occupied plot is atomic");
        check(s.build(-1,3,1)!=null,"bounds");
        check(s.build(7,2,1)!=null,"river protected");
        check(s.build(2,3,5)==null && s.build(2,2,1)==null,"new road unlocks land");
        check(s.canClaim(),"first quest progress");
        int before=s.coins, reward=s.claim();
        check(reward==100 && s.quest==1 && s.coins==before+100,"quest reward");
        check(s.claim()==0,"reward cannot be claimed twice");
        s.coins=10000;
        check(s.upgrade(3,2)==null && s.level(3,2)==2,"upgrade");
        check(s.upgrade(3,2)==null && s.level(3,2)==3,"second upgrade");
        before=s.coins;
        check(s.upgrade(3,2)!=null && s.coins==before,"max level");
        check(GameState.decode(s.encode()).encode().equals(s.encode()),"developed city save");
        check(s.demolish(3,2)==null && s.coins==before+135,"demolition refund");
        check(s.demolish(4,4)!=null && s.demolish(7,3)!=null,"infrastructure protected");
        s=new GameState();before=s.coins;
        for(int i=0;i<9;i++)check(!s.advance(1),"cycle timing");
        check(s.advance(1) && s.coins==before+s.income() && s.food==33,"economic cycle");
        s.paused=true;s.advance(1);check(s.elapsed==0,"pause");
        s.paused=false;s.food=999;
        for(int i=0;i<10;i++)s.advance(1);
        check(s.food==999,"storage cap");
        s.tiles[5+GameState.OFFSET][3+GameState.OFFSET]=0;s.levels[5+GameState.OFFSET][3+GameState.OFFSET]=0;s.food=0;
        for(int i=0;i<10;i++)s.advance(1);
        check(s.food==0 && s.happiness()>=20 && s.income()>0,"no starvation softlock");
        s.coins=0;
        check(s.build(3,2,1)!=null && s.tile(3,2)==0,"insufficient funds is atomic");
        check(GameState.decode("broken").coins==400,"corrupt save recovery");
        check(GameState.decode(s.encode().replaceFirst(";0;", ";-1;")).coins==400,"invalid currency recovery");
        // Long simulation and all objectives, including upgrades and final population goal.
        s=new GameState();s.coins=100000;
        s.build(3,2,1);s.build(5,2,1);check(s.claim()==100,"quest one");
        s.build(5,6,2);check(s.claim()==160,"quest two");
        s.build(3,6,3);check(s.claim()==220,"quest three");
        s.upgrade(3,3);s.upgrade(3,2);check(s.claim()==280,"quest four");
        s.build(0,0,4);s.build(0,1,4);check(s.claim()==340,"quest five");
        s.build(3,1,1);s.build(5,1,1);
        for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(s.tile(r,c)==1)while(s.level(r,c)<3)s.upgrade(r,c);
        check(s.population()==60 && s.claim()==400 && s.quest==6,"final objective");
        for(int i=0;i<36000;i++)s.advance(1);
        check(s.coins<=999999 && s.food>=0,"long simulation bounds");
        check(GameState.decode(s.encode()).encode().equals(s.encode()),"long session save");
        s=new GameState();s.coins=999999;s.build(3,2,1);s.build(5,2,1);s.coins=999999;s.claim();
        check(s.coins==999999 && GameState.decode(s.encode()).quest==1,"reward at currency cap preserves save");
        s.demolish(3,2);
        check(s.coins==999999 && GameState.decode(s.encode()).count(1)==2,"refund at currency cap preserves save");
        System.out.println("PASS: "+checks+" simulation checks");
    }
}
