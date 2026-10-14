import { Module } from '@nestjs/common';
import { CategoriesController } from './categories.controller';
import { ReportsController } from './reports.controller';

@Module({
  controllers: [CategoriesController, ReportsController],
})
export class AppModule {}
