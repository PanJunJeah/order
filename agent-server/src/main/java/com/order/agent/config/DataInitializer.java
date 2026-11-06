package com.order.agent.config;

import com.order.agent.domain.Dish;
import com.order.agent.repository.DishRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首次启动播种菜单：24 道菜，图片为 Wikimedia Commons 真实菜品照片（稳定外链）。
 *
 * <p>菜 1-10 与原演示数据完全一致（保证离线评测中依赖 dishId 的用例不变），
 * 11-24 为扩充菜品。仅在菜单表为空时执行，重启不会重复插入。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DishRepository dishRepository;

    public DataInitializer(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        long count = dishRepository.count();
        if (count > 0) {
            log.info("菜单已有 {} 道菜，跳过播种", count);
            return;
        }
        dishRepository.saveAll(seed());
        log.info("菜单播种完成：24 道菜（含真实图片 URL）");
    }

    private static List<Dish> seed() {
        return List.of(
                dish(1, "宫保鸡丁", "川菜", "鸡丁、花生、干辣椒，经典下饭", "38", 100, "🍗", true,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Kung_Pao_Chicken_at_Liqiaoshun_Restaurant_%2820220310175424%29.jpg/960px-Kung_Pao_Chicken_at_Liqiaoshun_Restaurant_%2820220310175424%29.jpg"),
                dish(2, "鱼香肉丝", "川菜", "咸甜酸辣兼备的经典川味", "32", 100, "🐟", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Rice_topped_with_Yuxiang_Rousi_at_Beixinqiao_%2820221031111236%29.jpg/960px-Rice_topped_with_Yuxiang_Rousi_at_Beixinqiao_%2820221031111236%29.jpg"),
                dish(3, "麻婆豆腐", "川菜", "麻辣鲜香，嫩豆腐配肉末", "28", 80, "🌶️", true,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/7/73/Authentic_Mapo_Tofu.jpg/960px-Authentic_Mapo_Tofu.jpg"),
                dish(4, "水煮鱼", "川菜", "鲜嫩鱼片，麻辣过瘾", "68", 50, "🐠", true,
                        "https://upload.wikimedia.org/wikipedia/commons/4/47/Shuizhufish2.JPG"),
                dish(5, "红烧肉", "家常菜", "肥而不腻，入口即化", "45", 60, "🥩", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Red_braised_pork_%2820141106191221%29.JPG/960px-Red_braised_pork_%2820141106191221%29.JPG"),
                dish(6, "番茄炒蛋", "家常菜", "酸甜开胃，家常味道", "22", 120, "🍅", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Tomato_scrambled_eggs_noodles.jpg/960px-Tomato_scrambled_eggs_noodles.jpg"),
                dish(7, "西湖牛肉羹", "汤羹", "牛肉末与蛋花，鲜美爽滑", "26", 70, "🥣", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Beef_Noodles_soup_from_Jinyi_Beef_Noodle_Soup_Inn_in_Shenzhen.jpg/960px-Beef_Noodles_soup_from_Jinyi_Beef_Noodle_Soup_Inn_in_Shenzhen.jpg"),
                dish(8, "白米饭", "主食", "东北大米，粒粒饱满", "3", 500, "🍚", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/A_bowl_of_rice.jpg/960px-A_bowl_of_rice.jpg"),
                dish(9, "可乐", "饮品", "冰镇可乐 330ml", "6", 200, "🥤", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Coca-cola_50cl_can_-_Italia.jpg/960px-Coca-cola_50cl_can_-_Italia.jpg"),
                dish(10, "酸梅汤", "饮品", "古法熬制，解辣解腻", "10", 150, "🧋", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/6/60/Sour_plum_drink.jpg/960px-Sour_plum_drink.jpg"),
                dish(11, "糖醋里脊", "家常菜", "外酥里嫩，酸甜可口", "42", 80, "🍖", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/Sweet_sour_pork.jpg/960px-Sweet_sour_pork.jpg"),
                dish(12, "回锅肉", "川菜", "肥瘦相间，酱香浓郁", "38", 70, "🥓", true,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Twice_cooked_pork_of_Gyoza-no-Manshu.jpg/960px-Twice_cooked_pork_of_Gyoza-no-Manshu.jpg"),
                dish(13, "鱼香茄子", "川菜", "软糯入味，鱼香四溢", "26", 90, "🍆", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Yuxiang_Eggplant_in_Hong_Kong.jpg/960px-Yuxiang_Eggplant_in_Hong_Kong.jpg"),
                dish(14, "干煸四季豆", "川菜", "干香爽脆，咸鲜微辣", "24", 85, "🫛", true,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/f/ff/Sichuan-style-dried_fried_Green_Beans_with_Minced_Pork_-_Aberdeen_Seafood%2C_Brighton_2026-07-19.jpg/960px-Sichuan-style-dried_fried_Green_Beans_with_Minced_Pork_-_Aberdeen_Seafood%2C_Brighton_2026-07-19.jpg"),
                dish(15, "辣子鸡", "川菜", "外焦里嫩，麻辣过瘾", "48", 60, "🌶️", true,
                        "https://commons.wikimedia.org/wiki/Special:FilePath/%E8%BE%A3%E5%AD%90%E9%B8%A1.jpg"),
                dish(16, "蛋炒饭", "主食", "粒粒分明，蛋香浓郁", "16", 150, "🍳", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c9/Egg_Fried_Rice.jpg/960px-Egg_Fried_Rice.jpg"),
                dish(17, "扬州炒饭", "主食", "配料丰富，经典炒饭", "22", 130, "🍤", false,
                        "https://upload.wikimedia.org/wikipedia/commons/4/44/Yangzhou_fried_rice_and_drinks_06-09-2019.jpg"),
                dish(18, "兰州拉面", "主食", "汤清面劲，牛肉飘香", "25", 110, "🍜", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/Chinese_Beef_Noodle_Soup_-_%E4%B8%AD%E5%BC%8F%E7%89%9B%E8%82%89%E6%B9%AF%E9%BA%B5.jpg/960px-Chinese_Beef_Noodle_Soup_-_%E4%B8%AD%E5%BC%8F%E7%89%9B%E8%82%89%E6%B9%AF%E9%BA%B5.jpg"),
                dish(19, "担担面", "川菜", "麻辣鲜香，肉臊浓郁", "20", 100, "🍝", true,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ee/Dandan_Noodles.jpg/960px-Dandan_Noodles.jpg"),
                dish(20, "饺子", "主食", "皮薄馅大，鲜香多汁", "18", 160, "🥟", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c0/Chengdu_Zhong_Dumpling%28Zhong_Jiaozi%29.jpg/960px-Chengdu_Zhong_Dumpling%28Zhong_Jiaozi%29.jpg"),
                dish(21, "小笼包", "主食", "汤汁饱满，鲜甜可口", "28", 90, "🥟", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f3/Truffle_Soup_Dumplings_at_Din_Tai_Fung.jpg/960px-Truffle_Soup_Dumplings_at_Din_Tai_Fung.jpg"),
                dish(22, "春卷", "家常菜", "金黄酥脆，鲜香可口", "15", 120, "🌯", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Spring_roll_2.jpg/960px-Spring_roll_2.jpg"),
                dish(23, "橙汁", "饮品", "鲜榨橙汁，维C满满", "12", 140, "🍊", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/960px-Orange_juice_1_edit1.jpg"),
                dish(24, "清炒时蔬", "家常菜", "清淡爽口，时令蔬菜", "18", 100, "🥬", false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Stir_fried_mixed_vegetables.jpg/960px-Stir_fried_mixed_vegetables.jpg")
        );
    }

    private static Dish dish(long id, String name, String category, String description,
                             String price, int stock, String emoji, boolean spicy, String image) {
        return new Dish(id, name, category, description, new BigDecimal(price), stock, emoji,
                image, spicy);
    }
}
