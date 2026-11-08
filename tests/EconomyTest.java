import com.cloudtown.game.GameState;

public final class EconomyTest {
    private static int checks;
    private static void check(boolean ok,String label) {checks++;if(!ok)throw new AssertionError(label);}
    private static void removeFarm(GameState s) {s.tiles[5+GameState.OFFSET][3+GameState.OFFSET]=0;s.levels[5+GameState.OFFSET][3+GameState.OFFSET]=0;}
    public static void main(String[] args) {
        GameState s=new GameState();s.food=0;
        check(s.foodPenaltyPercent()==0&&s.income()==29,"sufficient current production prevents false starvation");
        removeFarm(s);s.food=2;check(s.foodPenaltyPercent()==0,"two cycles of reserves are safe");
        s.food=1;check(s.foodPenaltyPercent()==20&&s.income()==8+(s.grossIncome()-8)*80/100,"low reserves reduce income");
        s.food=0;
        check(s.foodPenaltyPercent()==50&&s.comfortPenaltyPercent()==25,"starvation and comfort both apply");
        check(s.incomePenaltyPercent()==63&&s.income()==14,"multiplicative penalties protect base income");
        s.coins=500;int before=s.coins;
        check(s.build(3,2,GameState.HOUSE)!=null&&s.coins==before&&s.population()==4,"starvation blocks new housing atomically");
        s.legacyLevel=3;check(s.upgrade(3,3)!=null&&s.level(3,3)==1,"starvation blocks residential upgrades");
        check(s.build(5,3,GameState.FARM)==null&&s.foodPenaltyPercent()==0&&s.growthLock()==null,"farm repairs crisis immediately");
        check(s.build(3,2,GameState.HOUSE)==null,"growth resumes after food recovery");
        s=new GameState();s.coins=99999;
        for(int r=0;r<9&&s.population()<44;r++)for(int c=0;c<9&&s.population()<44;c++)
            if(s.tile(r,c)==0&&s.nearRoad(r,c))s.build(r,c,GameState.HOUSE);
        check(s.population()==44,"comfort fixture is reachable with ordinary construction");
        s.food=100;s.demolish(5,5);
        check(s.happiness()==32&&s.comfortPenaltyPercent()==50&&s.foodPenaltyPercent()==0,"critical comfort has its own penalty");
        before=s.coins;check(s.build(5,1,GameState.HOUSE)!=null&&s.coins==before,"low comfort stops residential growth");
        int badIncome=s.income();s.build(5,5,GameState.PARK);s.build(0,0,GameState.PARK);
        check(s.happiness()==50&&s.comfortPenaltyPercent()==25&&s.income()>badIncome,"parks improve income while in crisis");
        check(s.build(5,1,GameState.HOUSE)==null,"parks restart growth");
        s.build(0,1,GameState.PARK);s.build(0,2,GameState.PARK);
        check(s.happiness()>=60&&s.income()==s.grossIncome(),"comfortable city restores full income");
        s.food=0;check(s.foodPenaltyPercent()==50&&s.incomePenaltyPercent()<=75&&s.income()>=8,"combined loss capped at 75 percent of variable income");
        // Even a bankrupt starving town can buy food production again from base income.
        s=new GameState();removeFarm(s);s.food=0;s.coins=0;
        int seconds=0;while(s.coins<120&&seconds<200){s.advance(1);seconds++;}
        check(s.coins>=120&&seconds<=150,"bankruptcy recovery without gifts or resets");
        check(s.build(5,3,GameState.FARM)==null&&s.foodPenaltyPercent()==0,"recovered city can fund a farm");
        // Awards and popup acknowledgement are separate, persistent and idempotent.
        s=new GameState();s.peakPopulation=60;s.refreshProgress();
        check(s.coins==700&&s.food==60&&s.stageRewardMask==3,"24 and 60 rewards credited once");
        check(s.stageCoinsReceived(0)==100&&s.stageFoodReceived(1)==20&&s.nextStageNotice()==0,"exact reward details and ordered notifications");
        int coins=s.coins,food=s.food;s.refreshProgress();s.maxLevel();s.questProgress();
        check(s.coins==coins&&s.food==food,"queries do not duplicate rewards");
        s=GameState.decode(s.encode());check(s.coins==coins&&s.nextStageNotice()==0,"unread popup survives restart without second payout");
        s.acknowledgeStage(0);check(s.nextStageNotice()==1,"next pending stage shown in order");
        s.acknowledgeStage(1);s.acknowledgeStage(1);s=GameState.decode(s.encode());
        check(s.nextStageNotice()==-1&&s.coins==coins,"acknowledged stages never repeat");
        s=new GameState();s.coins=999950;s.food=995;s.peakPopulation=24;s.refreshProgress();
        check(s.coins==999999&&s.food==999&&s.stageCoinsReceived(0)==49&&s.stageFoodReceived(0)==4,"popup reports actual payout at storage limits");
        s=new GameState();s.peakPopulation=800;s.refreshProgress();
        check(s.coins==3500&&s.food==270&&s.stageRewardMask==63,"all stage rewards on a population jump");
        // Version 1.3 had no stage reward ledger. Its city receives the new awards once.
        s=new GameState();s.peakPopulation=60;s.stageRewardMask=3;s.stageSeenMask=3;
        String v4=s.encode();int start=v4.indexOf("P,");String[] meta=v4.substring(start,v4.indexOf(';',start)).split(",");
        StringBuilder v3=new StringBuilder(v4.substring(0,start).replaceFirst("^4;","3;"));
        for(int i=0;i<7;i++)v3.append(i==0?"":",").append(meta[i]);v3.append(';');
        GameState migrated=GameState.decode(v3.toString());
        check(migrated.coins==700&&migrated.food==60&&migrated.nextStageNotice()==0,"version 1.3 retroactive awards");
        check(GameState.decode(migrated.encode()).coins==700,"migration does not pay again on later loads");
        s=new GameState();removeFarm(s);s.food=1;
        int forecast=s.income();coins=s.coins;for(int i=0;i<10;i++)s.advance(1);
        check(s.coins-coins==forecast&&s.food==0,"income forecast matches actual credit when reserves run out");
        forecast=s.income();coins=s.coins;for(int i=0;i<10;i++)s.advance(1);
        check(s.coins-coins==forecast&&forecast==14,"starvation forecast matches next credit");
        s=new GameState();s.coins=500;s.eventId=2;s.resolveEvent(0);
        forecast=s.income();coins=s.coins;for(int i=0;i<120;i++)s.advance(1);
        check(s.coins-coins==forecast*12&&s.income()<forecast,"festival bonus lasts exactly twelve paid cycles");
        s=new GameState();s.coins=5000;s.legacyLevel=3;
        s.upgrade(3,3);s.upgrade(3,3);s.build(3,2,GameState.HOUSE);s.upgrade(3,2);
        s.food=2;check(s.foodUse()==5&&s.foodProduction()==4&&s.foodPenaltyPercent()==0,"reserve calculation uses net food deficit");
        s.food=1;check(s.foodPenaltyPercent()==20,"one deficit cycle is a warning");
        s.food=0;check(s.foodPenaltyPercent()==50,"no deficit coverage is starvation");
        System.out.println("PASS: "+checks+" economy, crisis recovery and reward ledger checks");
    }
}
