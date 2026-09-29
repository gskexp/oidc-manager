import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'categories' },
  {
    path: 'categories',
    title: 'Reports',
    loadComponent: () => import('./components/category-list/category-list.component').then((m) => m.CategoryListComponent),
  },
  {
    path: 'categories/:categoryId',
    title: 'Category · Reports',
    loadComponent: () => import('./components/sub-category-list/sub-category-list.component').then((m) => m.SubCategoryListComponent),
  },
  {
    path: '**',
    title: 'Page not found · Reports',
    loadComponent: () => import('./components/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
