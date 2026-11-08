package com.cloudtown.game;

/** Cosmetic edge decorations; they never change building capacity or income. */
public final class DecorCatalog {
    public static final int COUNT=18;
    public static final int[] MILESTONES={24,60,120,240,480,800};
    public static final String[] NAMES={"","Клумба","Куст","Камни","Скамейка","Фонарь","Указатель",
        "Заборчик","Цветы","Кашпо","Дорожка","Скульптура","Навес","Фонтан","Гирлянда","Ель","Памятник","Арка","Флаг"};
    public static final int[] COST={0,10,10,10,20,25,15,25,20,25,30,45,35,80,40,50,120,100,60};
    public static int required(int kind) {return MILESTONES[(kind-1)/3];}
    public static boolean valid(int kind) {return kind>=1&&kind<=COUNT;}
}
