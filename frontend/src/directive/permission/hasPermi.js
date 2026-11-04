/** Operation-permission directive adapted from RuoYi-Vue3. Copyright (c) 2019 RuoYi; MIT. */
import useUserStore from "@/store/modules/user";
export default {
  mounted(el, binding) {
    const { value } = binding;
    if (!Array.isArray(value) || !value.length)
      throw new Error("Provide at least one operation permission.");
    const allowed = useUserStore().permissions.some(
      (permission) => permission === "*:*:*" || value.includes(permission),
    );
    if (!allowed) el.parentNode?.removeChild(el);
  },
};
