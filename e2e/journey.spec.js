import { test, expect } from '@playwright/test';

const viewports = [
  { name: 'mobile', width: 390, height: 844 },
  { name: 'desktop', width: 1280, height: 800 },
];

for (const viewport of viewports) {
  test(`nine-step research journey ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.getByRole('link', { name: /gyanwire/i }).first()).toBeVisible();
    await page.goto('/pricing');
    await expect(page.getByRole('heading', { name: /simple plans/i })).toBeVisible();
    await page.goto('/terms');
    await expect(page.getByText('REVIEW WITH LAWYER')).toBeVisible();
    await page.goto('/privacy');
    await expect(page.getByRole('heading', { name: 'Privacy' })).toBeVisible();
    await page.goto('/refund');
    await expect(page.getByRole('heading', { name: 'Refunds' })).toBeVisible();
    await page.goto('/contact');
    await expect(page.getByRole('heading', { name: 'Contact' })).toBeVisible();
    await page.goto('/');
    await expect(page.getByLabel(/research/i).first()).toBeVisible();
  });
}
