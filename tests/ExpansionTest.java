import com.cloudtown.game.GameState;

public final class ExpansionTest {
    private static int checks;
    private static void check(boolean ok,String name) {checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args) {
        GameState s=new GameState();
        check(s.ownedCount()==81&&s.availablePlots().size()==8,"starter frontier");
        check(s.buyPlot(-4,0)==null&&s.coins==240&&s.ownedCount()==97,"north purchase costs 160 and adds 16 cells");
        check(s.valid(-4,0)&&s.valid(-1,3)&&!s.valid(-5,0)&&!s.valid(-4,4),"exact 4x4 footprint");
        int coins=s.coins;
        check(s.buyPlot(-4,0)!=null&&s.coins==coins,"duplicate purchase is atomic");
        check(s.buyPlot(-12,0)!=null&&s.coins==coins,"no detached purchases");
        check(s.build(-1,3,GameState.ROAD)==null&&s.build(-2,3,GameState.HOUSE)==null,"construction at negative coordinates");
        s.coins=5000;check(s.buyPlot(-8,0)==null&&s.landCost()==280,"repeat outward expansion and increasing price");
        check(s.buyPlot(0,-4)==null&&s.buyPlot(9,0)==null&&s.buyPlot(0,9)==null,"all four directions");
        String saved=s.encode();check(GameState.decode(saved).encode().equals(saved),"expanded city roundtrip");
        check(GameState.decode(saved).tile(-2,3)==GameState.HOUSE,"negative-coordinate building persists");
        s=new GameState();s.coins=159;
        check(s.buyPlot(-4,0)!=null&&s.ownedCount()==81&&s.coins==159,"cannot buy without coins");
        check(s.build(-1,0,GameState.PARK)!=null,"cannot build on unowned land");
        s=new GameState();int production=s.foodProduction();coins=s.coins;
        check(s.build(1,1,GameState.FISH)!=null&&s.coins==coins,"fishing hut requires river");
        check(s.build(7,1,GameState.FISH)!=null,"hut cannot occupy water");
        check(!s.nearRoad(6,1)&&s.build(6,1,GameState.FISH)==null,"shore hut does not need road");
        check(s.coins==280&&s.foodProduction()==production+4,"same cost and food as farm");
        s.coins=5000;s.legacyLevel=3;check(s.upgrade(6,1)==null&&s.foodProduction()==production+8,"upgraded fishing output");
        check(s.upgrade(6,1)==null&&s.foodProduction()==production+12,"level three fishing output");
        check(s.upgrade(6,1)!=null,"fishing max level");
        check(GameState.decode(s.encode()).level(6,1)==3,"fishing save");
        coins=s.coins;check(s.demolish(6,1)==null&&s.coins==coins+180&&s.foodProduction()==production,"fishing demolition and refund");
        check(s.buyPlot(4,-4)==null&&s.tile(7,-2)==GameState.WATER,"river extends into purchased territory");
        check(s.build(6,-2,GameState.FISH)==null,"fishing on new coast");
        int food=s.food;for(int i=0;i<10;i++)s.advance(1);
        check(s.food==food+s.foodProduction()-s.foodUse(),"fishing contributes each economic cycle");
        // A real version-1 payload, with non-default funds, progress and an improved house.
        GameState old=new GameState();old.coins=4444;old.legacyLevel=3;old.upgrade(3,3);
        StringBuilder legacy=new StringBuilder("1;").append(old.coins).append(";73;17;2;4.5;0;");
        for(int r=0;r<9;r++)for(int c=0;c<9;c++)legacy.append(old.tile(r,c)).append(',').append(old.level(r,c)).append(';');
        GameState migrated=GameState.decode(legacy.toString());
        check(migrated.coins==old.coins&&migrated.food==73&&migrated.ticks==17&&migrated.quest==2&&!migrated.sound,"legacy city migration preserves metadata");
        check(migrated.level(3,3)==2&&migrated.ownedCount()==81,"legacy buildings preserved");
        check(migrated.buyPlot(-4,0)==null&&GameState.decode(migrated.encode()).level(3,3)==2,"legacy city can expand and save");
        // Exhaust the finite world: each purchase is adjacent, disjoint and adds precisely 16 cells.
        s=new GameState();int bought=0;
        while(!s.availablePlots().isEmpty()) {
            GameState.Plot p=s.availablePlots().get(0);s.coins=999999;
            check(s.buyPlot(p.r,p.c)==null,"frontier purchase "+bought);bought++;
            check(s.ownedCount()==81+16*bought,"area after purchase "+bought);
            if(bought>100)throw new AssertionError("unbounded expansion");
        }
        check(bought>20&&s.ownedCount()<=GameState.WORLD_SIZE*GameState.WORLD_SIZE,"world boundary");
        check(GameState.decode(s.encode()).encode().equals(s.encode()),"fully expanded city save");
        System.out.println("PASS: "+checks+" expansion, fishing and migration checks");
    }
}
