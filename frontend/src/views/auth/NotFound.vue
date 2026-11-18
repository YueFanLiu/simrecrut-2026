<!-- Explain missing routes and denied workspace access. -->
<template>
  <main>
    <h1>{{ $t("Page unavailable") }}</h1>
    <p>{{ $t("The address is unknown or your account does not have permission.") }}</p>
    <RouterLink to="/login">{{ $t("Return to sign in") }}</RouterLink>
  </main>
</template>
