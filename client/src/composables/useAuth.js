import { storeToRefs } from 'pinia';
import { useAuthSessionStore } from '../stores/authSession.store.js';
import { useAuthFormStore } from '../stores/authForm.store.js';
import { useUiStore } from '../stores/ui.store.js';
import {
  loginWithGoogle,
  loginWithPassword,
  logoutUser,
  openLoginPopup,
  openRegisterPopup,
  registerAccount,
} from '../services/auth.service.js';

/**
 * Single auth surface for components — composable over Pinia + services.
 */
export function useAuth() {
  const authSession = useAuthSessionStore();
  const authForm = useAuthFormStore();
  const ui = useUiStore();

  const {
    userId,
    userDisplayName,
    fullName,
    email,
    firstName,
    lastName,
    role,
    profilePic,
    isAuthenticated,
    shortName,
    greetingName,
    loginDisplayName,
  } = storeToRefs(authSession);

  const {
    identity,
    password,
    emailError,
    authError,
    loginInfoMessage,
    registerFirstName,
    registerLastName,
    registerEmail,
    registerPassword,
    registerError,
    isSubmitting,
    isGoogleSubmitting,
    canSubmitLogin,
  } = storeToRefs(authForm);

  const { isLoginOpen, isRegisterOpen, isAnyPopupOpen } = storeToRefs(ui);

  return {
    // session
    userId,
    userDisplayName,
    fullName,
    email,
    firstName,
    lastName,
    role,
    profilePic,
    isAuthenticated,
    shortName,
    greetingName,
    loginDisplayName,

    // form
    identity,
    password,
    emailError,
    authError,
    loginInfoMessage,
    registerFirstName,
    registerLastName,
    registerEmail,
    registerPassword,
    registerError,
    isSubmitting,
    isGoogleSubmitting,
    canSubmitLogin,

    // popups
    isLoginOpen,
    isRegisterOpen,
    isAnyPopupOpen,
    openLoginPopup,
    openRegisterPopup,
    closePopup: () => ui.closePopup(),

    // actions
    setIdentity: (value) => authForm.setIdentity(value),
    setPassword: (value) => authForm.setPassword(value),
    loginWithPassword,
    loginWithGoogle,
    registerAccount,
    logoutUser,
  };
}
