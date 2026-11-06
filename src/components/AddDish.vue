<template>
  <div class="add-dish-page">
    <div class="panel-card form-card">
      <div class="panel-head">
        <h2 class="page-title">➕ 上架新菜品</h2>
      </div>

      <el-form :model="form" label-width="90px" class="dish-form">
        <el-form-item label="菜品图标">
          <div class="emoji-picker">
            <span
              v-for="e in emojis"
              :key="e"
              class="emoji-option"
              :class="{ active: form.emoji === e }"
              @click="form.emoji = e"
            >{{ e }}</span>
          </div>
        </el-form-item>

        <el-form-item label="菜品名称" required>
          <el-input v-model="form.name" placeholder="如：糖醋里脊" maxlength="20" show-word-limit />
        </el-form-item>

        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0.5" :precision="1" :step="0.5" />
          <span class="unit">元</span>
        </el-form-item>

        <el-form-item label="库存" required>
          <el-input-number v-model="form.stock" :min="0" :precision="0" />
          <span class="unit">份</span>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="60"
            show-word-limit
            placeholder="一句话介绍这道菜…"
          />
        </el-form-item>

        <el-form-item label="图片URL">
          <el-input v-model="form.image" placeholder="可选，如 https://…/dish.jpg" clearable />
        </el-form-item>

        <el-form-item label="辣度">
          <el-switch v-model="form.spicy" active-text="辣" inactive-text="不辣" />
        </el-form-item>

        <el-form-item>
          <div class="form-actions">
            <el-button type="primary" round :loading="saving" @click="submit">上架</el-button>
            <el-button round @click="reset">重置</el-button>
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script>
import { reactive, ref } from 'vue';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';
import { api } from '../api';

const EMOJIS = ['🍗', '🐟', '🌶️', '🐠', '🥩', '🍅', '🥣', '🍚', '🥤', '🧋', '🍜', '🥟', '🍤', '🥬', '🍖', '🍲'];

export default {
  setup() {
    const store = useStore();
    const saving = ref(false);
    const categories = ['川菜', '家常菜', '汤羹', '主食', '饮品'];

    const form = reactive({
      emoji: '🍽️',
      name: '',
      category: '家常菜',
      price: 20,
      stock: 100,
      description: '',
      image: '',
      spicy: false
    });

    const submit = async () => {
      if (!form.name.trim()) {
        ElMessage.warning('请填写菜品名称');
        return;
      }
      if (!form.price || form.price <= 0) {
        ElMessage.warning('价格必须大于 0');
        return;
      }
      saving.value = true;
      try {
        const res = await api.addDish({
          name: form.name.trim(),
          category: form.category,
          description: form.description,
          price: Number(form.price),
          stock: Number(form.stock),
          emoji: form.emoji,
          image: form.image.trim() || null,
          spicy: form.spicy
        });
        store.commit('addDish', res.data);
        ElMessage.success(`「${res.data.name}」上架成功！`);
        reset();
      } catch (e) {
        ElMessage.error(`上架失败：${e.message}`);
      } finally {
        saving.value = false;
      }
    };

    const reset = () => {
      form.emoji = '🍽️';
      form.name = '';
      form.category = '家常菜';
      form.price = 20;
      form.stock = 100;
      form.description = '';
      form.image = '';
      form.spicy = false;
    };

    return { emojis: EMOJIS, categories, form, saving, submit, reset };
  }
};
</script>

<style scoped>
.add-dish-page {
  display: flex;
  justify-content: center;
}

.form-card {
  width: 560px;
  padding: 20px 24px;
}

.panel-head {
  margin-bottom: 18px;
}

.emoji-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.emoji-option {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  border-radius: 10px;
  background: var(--bg-page);
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.15s;
}

.emoji-option:hover {
  transform: scale(1.1);
}

.emoji-option.active {
  border-color: var(--brand-primary);
  background: var(--brand-soft);
}

.unit {
  margin-left: 10px;
  color: var(--ink-500);
}

.form-actions {
  display: flex;
  gap: 10px;
}
</style>
